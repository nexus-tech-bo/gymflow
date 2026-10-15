---
name: spring-multi-tenancy
description: 'Implement schema-per-tenant multi-tenancy in a Spring Boot + Hibernate 6/7 + PostgreSQL + Flyway application. Use when the user asks to add multi-tenancy, tenant isolation, schema-per-tenant, tenant provisioning, or to route JPA queries to a tenant schema.'
---

# Spring Multi-Tenancy (Schema per Tenant)

Your goal is to implement **schema-based multi-tenancy**: one shared `public` schema for global data (tenants registry, accounts, catalogs) and one PostgreSQL schema per tenant (e.g. `tenant_42`) with identical tables. Hibernate picks the schema per session through `search_path`.

Before writing code, inspect the target project and adapt:
- Base package, Spring Boot version (3.x vs 4.x imports differ, see step 4), Lombok usage, existing auth filter.
- How the tenant is identified: JWT claim, header, subdomain, login request field.
- Which entities are global (stay in `public`) and which are tenant-scoped.
- Pick a schema prefix (`<prefix>_<id>`, e.g. `tenant_`, `clinic_`, `org_`) and use it consistently. Below it is written `tenant_`.

## Architecture

```
HTTP request
  └─ Auth filter ── reads tenant id ──> TenantContext (ThreadLocal)   [cleared in finally]
        └─ @Transactional / repository call
              └─ Hibernate opens Session
                    ├─ CurrentTenantIdentifierResolver -> TenantContext or "public"
                    └─ MultiTenantConnectionProvider   -> SET search_path TO tenant_X, public
```

Files to create (under `<base>.config.tenant` unless the project has another convention):

| File                                                     | Role                                                          |
|----------------------------------------------------------|---------------------------------------------------------------|
| `TenantContext`                                          | ThreadLocal holder of the current tenant schema               |
| `CustomTenantResolver`                                   | Hibernate `CurrentTenantIdentifierResolver`                   |
| `SchemaBasedMultiTenantConnectionProvider`               | Hibernate `MultiTenantConnectionProvider`, sets `search_path` |
| `HibernateConfig`                                        | Registers the two beans above in Hibernate properties         |
| `SchemaProvisioningService`                              | Creates/migrates tenant schemas with Flyway                   |
| `db/migration/public/*.sql`, `db/migration/tenant/*.sql` | Separate migration sets                                       |

## 1. TenantContext

```java
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentTenant(String tenantId) { CURRENT_TENANT.set(tenantId); }

    public static String getCurrentTenant() { return CURRENT_TENANT.get(); }

    public static void clear() { CURRENT_TENANT.remove(); }
}
```

## 2. Tenant identifier resolver

```java
@Component
public class CustomTenantResolver implements CurrentTenantIdentifierResolver<String> {
    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        var tenant = TenantContext.getCurrentTenant();
        return (tenant != null && !tenant.isBlank()) ? tenant : DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
```

## 3. Connection provider

`search_path` cannot be bound as a JDBC parameter, so the identifier is concatenated into SQL. **Always validate it** against a strict pattern here (not only at provisioning time) to prevent SQL injection.

```java
@Component
@RequiredArgsConstructor
public class SchemaBasedMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {
    private static final Pattern SCHEMA = Pattern.compile("^(public|tenant_\\d+)$");

    private final DataSource dataSource;

    @Override
    public Connection getAnyConnection() throws SQLException {
        var connection = dataSource.getConnection();
        setSearchPath(connection, "public");
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        if (!SCHEMA.matcher(tenantIdentifier).matches())
            throw new SQLException("Invalid tenant identifier: " + tenantIdentifier);
        var connection = dataSource.getConnection();
        setSearchPath(connection, tenantIdentifier + ", public");
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        setSearchPath(connection, "public"); // pooled connections must not leak a tenant
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() { return false; }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) { return false; }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("Unwrap not supported");
    }

    private void setSearchPath(Connection connection, String path) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + path);
        }
    }
}
```

Keeping `public` in the tenant search path lets tenant tables reference global tables (FKs to `public.accounts`, etc.).

## 4. Register in Hibernate

```java
@Configuration
@RequiredArgsConstructor
public class HibernateConfig implements HibernatePropertiesCustomizer {
    private final SchemaBasedMultiTenantConnectionProvider connectionProvider;
    private final CustomTenantResolver tenantResolver;

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
    }
}
```

`HibernatePropertiesCustomizer` import:
- Spring Boot 4.x: `org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer`
- Spring Boot 3.x: `org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer`

In Hibernate 6+, registering a `MULTI_TENANT_CONNECTION_PROVIDER` is what enables multi-tenancy; the old `hibernate.multiTenancy: SCHEMA` property is no longer required.

## 5. application.yaml

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none          # schemas are owned by Flyway
    open-in-view: false       # avoid sessions outliving the TenantContext
  flyway:
    locations: classpath:db/migration/public   # auto-run only migrates public
    baseline-on-migrate: true
```

Dependencies (Gradle): `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway` (Boot 4) or `org.flywaydb:flyway-core` (Boot 3), `runtimeOnly 'org.flywaydb:flyway-database-postgresql'`, `runtimeOnly 'org.postgresql:postgresql'`.

## 6. Migrations

- `db/migration/public/V1__init_public_schema.sql`: global tables, fully qualified with `public.`.
- `db/migration/tenant/V1__init_tenant_schema.sql`: tenant tables using `${flyway:defaultSchema}` placeholder; reference globals as `public.<table>`. Prefix constraint names with the schema to keep them unique and readable:

```sql
CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.items (
    id BIGSERIAL PRIMARY KEY,
    owner_account_id BIGINT NOT NULL REFERENCES public.accounts(id),
    name VARCHAR(255) NOT NULL,
    CONSTRAINT uq_${flyway:defaultSchema}_items_name UNIQUE (name)
);
```

Each tenant schema gets its own `flyway_schema_history`, so tenants migrate independently.

## 7. Entities

- Global entities: `@Table(name = "accounts", schema = "public")`.
- Tenant entities: `@Table(name = "items")` with **no schema**, so `search_path` resolves them.
- Tenant → global `@ManyToOne` is fine. Avoid global → tenant relations.

## 8. Schema provisioning

Creates and migrates a tenant schema; also re-migrates every existing tenant at startup so new tenant migrations roll out on deploy.

```java
@Service
@RequiredArgsConstructor
public class SchemaProvisioningService implements ApplicationRunner {
    private static final String TENANT_MIGRATIONS = "classpath:db/migration/tenant";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.queryForList("SELECT id FROM public.tenants", Long.class)
                .forEach(id -> createTenantSchema("tenant_" + id));
    }

    public void createTenantSchema(String schemaName) {
        if (schemaName == null || !schemaName.matches("^tenant_\\d+$"))
            throw new IllegalArgumentException("Invalid tenant schema name: " + schemaName);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)        // Flyway creates the schema if missing
                .locations(TENANT_MIGRATIONS)
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }
}
```

Do not add an interface for it unless the project already uses the interface + impl convention.

## 9. Setting the tenant per request

In the existing auth filter (or a dedicated `OncePerRequestFilter` placed after authentication), set the tenant and **always clear it in `finally`** — servlet threads are pooled.

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
    try {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwtUtil.parse(header.substring(7)).ifPresent(claims -> {
                TenantContext.setCurrentTenant("tenant_" + claims.get("tenantId", Long.class));
                // ... set SecurityContext authentication
            });
        }
        chain.doFilter(request, response);
    } finally {
        TenantContext.clear();
    }
}
```

Derive the tenant only from a trusted source (signed JWT claim, server-side lookup). Never trust a raw client header for an authenticated user without checking membership.

## 10. Switching tenant programmatically

For login, onboarding, seeders, schedulers: set the context **before** the transaction opens (Hibernate resolves the tenant when the session starts), then clear it.

```java
var tx = new TransactionTemplate(transactionManager);
var tenant = tx.execute(_ -> tenantRepository.save(newTenant));   // runs in public
var schema = "tenant_" + tenant.getId();
schemaProvisioningService.createTenantSchema(schema);
try {
    TenantContext.setCurrentTenant(schema);
    return tx.execute(_ -> {
        // repository calls here hit tenant_<id>
    });
} finally {
    TenantContext.clear();
}
```

Do not switch tenants inside a single `@Transactional` method: the session is already bound to the first tenant. Use separate `TransactionTemplate` executions instead.

## Pitfalls checklist

- [ ] Tenant id validated with a strict regex wherever it reaches SQL.
- [ ] `TenantContext.clear()` in `finally` on every `set`.
- [ ] `spring.jpa.open-in-view: false`.
- [ ] `search_path` reset to `public` on connection release.
- [ ] `@Async`, `CompletableFuture`, `@Scheduled` and virtual threads do not inherit the ThreadLocal: pass the tenant explicitly and set/clear it in the task (or use a `TaskDecorator`).
- [ ] Spring Flyway auto-config only points to `public` migrations; tenant migrations run via the provisioning service.
- [ ] Second-level cache and query cache keys must be tenant-aware (Hibernate handles this when multi-tenancy is enabled; custom caches must include the tenant).
- [ ] Integration test: two tenants, insert in one, assert the other cannot see it.
