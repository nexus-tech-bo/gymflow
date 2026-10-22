---
name: java-common-config
description: Common cross-cutting configuration for a Spring Boot REST backend - CORS, OpenAPI/Swagger UI with JWT bearer auth, global exception handler with a uniform error JSON, application.yaml profiles and environment variables, Clock bean and time zone, Flyway, JPA defaults, pagination, and data seeding. Use when setting up a new Spring Boot project, configuring CORS or Swagger, standardizing API errors, adding profiles/env vars, or troubleshooting preflight/CORS, Swagger auth, or config issues.
---

# Common Spring Boot Configuration

Cross-cutting setup that almost every REST backend needs. Examples use a fictional **library** API.

## 1. Properties and profiles

`src/main/resources/application.yaml` — defaults, no secrets:

```yaml
spring:
  config:
    import: optional:file:.env[.properties]   # local secrets file, git-ignored
  application:
    name: library
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: none          # Flyway owns the schema
    open-in-view: false       # no lazy loading in controllers/JSON serialization
  flyway:
    locations: classpath:db/migration
    baseline-on-migrate: true

server:
  port: ${PORT:8080}

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}

jwt:
  secret: ${JWT_SECRET}

security:
  public-paths: /api/auth/login, /swagger-ui/**, /v3/api-docs/**

app:
  zone: ${APP_ZONE:UTC}
```

`application-dev.yaml` — activated with `SPRING_PROFILES_ACTIVE=dev`:

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
logging:
  level:
    org.hibernate.orm.jdbc.bind: TRACE   # shows bound parameter values
```

`.env` (git-ignored) + `.env.example` (committed with empty values):

```properties
DB_URL=jdbc:postgresql://localhost:5432/library
DB_USERNAME=postgres
DB_PASSWORD=
JWT_SECRET=
```

Rules:
- Every secret is a `${ENV_VAR}` placeholder. Never commit real values.
- Give non-secret values a default (`${PORT:8080}`); leave secrets without default so startup fails loudly if missing.
- Group custom properties under your own prefixes (`app.*`, `cors.*`, `security.*`). For more than a couple of related values, bind them to a `@ConfigurationProperties` record instead of many `@Value`s:

```java
@ConfigurationProperties(prefix = "app.loans")
public record LoanProperties(int maxActivePerMember, Duration defaultDuration) {}
// enable with @ConfigurationPropertiesScan on the application class
```

## 2. CORS

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
```

- Comma-separated env var → `String[]`: `CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com`.
- `allowCredentials(false)` is right for bearer tokens in headers. If you use cookies, set it to `true` and list exact origins (or `allowedOriginPatterns`) — `*` is rejected with credentials.
- With Spring Security, also call `.cors(Customizer.withDefaults())` in the `SecurityFilterChain`, or preflight `OPTIONS` requests get 401 before reaching MVC.
- Expose custom response headers the browser must read: `.exposedHeaders("Content-Disposition")`.

## 3. OpenAPI / Swagger UI (springdoc)

```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:<version matching your Boot major>'
```

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Library API").version("v1").description("Library management REST API"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

- Adds an **Authorize** button in Swagger UI; paste the token without the `Bearer ` prefix.
- UI at `/swagger-ui/index.html`, spec at `/v3/api-docs`. Both must be in `security.public-paths`.
- Annotate controllers: `@Tag(name = "Books")` on the class, `@Operation(summary = "GET /api/books/{id} — get a book by id")` on methods.
- Mark public endpoints with `@SecurityRequirements` (empty) to hide the lock icon.
- Disable in production if the API is private: `springdoc.api-docs.enabled: false`, `springdoc.swagger-ui.enabled: false`.

## 4. Global exception handler

Pairs with the `ApiException` hierarchy and `ErrorResponse` record (see `java-mvc-project-structure`).

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)          // @Valid @RequestBody
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.FieldErrorItem(e.getField(), e.getDefaultMessage()))
                .toList();
        return validationFailed(errors, req);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)         // constraints on @RequestParam/@PathVariable
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        var errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ErrorResponse.FieldErrorItem(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return validationFailed(errors, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        var errors = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validationFailed(errors, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " not supported", req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The operation conflicts with existing data", req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "The resource was modified by another request, retry", req);
    }

    @ExceptionHandler(PropertyReferenceException.class)                 // ?sort=unknownField
    public ResponseEntity<ErrorResponse> handlePropertyReference(PropertyReferenceException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Invalid sort property '" + ex.getPropertyName() + "'", req);
    }

    @ExceptionHandler(AccessDeniedException.class)                     // @PreAuthorize failures
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Access denied", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req);
    }

    private ResponseEntity<ErrorResponse> validationFailed(List<ErrorResponse.FieldErrorItem> errors, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Validation failed", req.getRequestURI(), errors));
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, req.getRequestURI()));
    }
}
```

Resulting JSON:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/books",
  "timestamp": "2026-01-15T10:30:00",
  "errors": [{ "field": "isbn", "message": "must not be blank" }]
}
```

- Never leak stack traces, SQL or exception class names to the client; log them server-side.
- 4xx caused by clients → `debug`/`warn`; unexpected 5xx → `error` with the stack trace.

## 5. Time: Clock bean and zone

```java
@Bean
public Clock clock(@Value("${app.zone:UTC}") String zone) {
    return Clock.system(ZoneId.of(zone));
}
```

Services use `LocalDate.now(clock)` / `LocalDateTime.now(clock)`. Tests inject `Clock.fixed(...)`. Business rules ("today", "is overdue") then follow the business time zone regardless of the server's.

## 6. Pagination defaults

```java
public ResponseEntity<Page<BookResponse>> findAllBooks(
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) { ... }
```

```yaml
spring:
  data:
    web:
      pageable:
        max-page-size: 100
        serialization-mode: via-dto   # stable JSON shape for Page<T>
```

Clients call `?page=0&size=20&sort=title,asc`. Invalid sort fields are mapped to 400 by the handler above.

## 7. Flyway

```
src/main/resources/db/migration/
├── V1__create_authors.sql
├── V2__create_books.sql
└── V3__add_isbn_index.sql
```

- `V{n}__snake_description.sql`, never edit an applied file.
- `baseline-on-migrate: true` when adding Flyway to an existing database.
- Gradle (Boot 4): `spring-boot-starter-flyway` + `flyway-database-postgresql` (runtime) for PostgreSQL.

## 8. Data seeding (dev/demo data)

```java
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {
    private final AuthorRepository authorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (authorRepository.count() > 0) return;   // idempotent
        authorRepository.save(Author.builder().name("Jane Doe").build());
    }
}
```

Reference data that every environment needs belongs in a Flyway migration, not in a seeder.

## 9. Other useful beans

```java
@Bean
public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }   // own @Configuration class

@Bean
public RestClient restClient(RestClient.Builder builder) {                         // outbound HTTP
    return builder.baseUrl("https://api.example.com").build();
}
```

Async/scheduling when needed: `@EnableAsync`, `@EnableScheduling` on a config class plus a named `ThreadPoolTaskExecutor`.

## Checklist

- [ ] Secrets only in env vars / git-ignored `.env`; `.env.example` committed
- [ ] `open-in-view: false`, `ddl-auto: none`, Flyway enabled
- [ ] CORS origins from config and `.cors()` enabled in Spring Security
- [ ] Swagger with bearer scheme; docs paths public (or disabled in prod)
- [ ] One `GlobalExceptionHandler` returning `ErrorResponse` for every error
- [ ] `Clock` bean with business time zone
- [ ] Pagination defaults and max page size
