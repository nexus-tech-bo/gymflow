package com.example.gymflow.service.impl;

import com.example.gymflow.config.tenant.TenantContext;
import com.example.gymflow.service.SchemaProvisioningService;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Runs the tenant Flyway migrations on a gym schema; at startup it migrates every existing gym.
 */
@Service
@RequiredArgsConstructor
public class SchemaProvisioningServiceImpl implements SchemaProvisioningService, ApplicationRunner {
    private static final String TENANT_MIGRATIONS = "classpath:db/migration/tenant";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        jdbcTemplate.queryForList("SELECT id FROM public.gyms", Long.class)
                .forEach(id -> createTenantSchema(TenantContext.schemaOf(id)));
    }

    @Override
    public void createTenantSchema(String schemaName) {
        validateSchemaName(schemaName);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations(TENANT_MIGRATIONS)
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    @Override
    public void dropTenantSchema(String schemaName) {
        validateSchemaName(schemaName); // the name is concatenated into the DDL
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
    }

    private void validateSchemaName(String schemaName) {
        if (schemaName == null || !schemaName.matches("^gym_\\d+$"))
            throw new IllegalArgumentException("Invalid tenant schema name: " + schemaName);
    }
}
