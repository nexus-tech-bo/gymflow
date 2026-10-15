package com.example.gymflow.service;

/**
 * Creates and migrates tenant schemas.
 */
public interface SchemaProvisioningService {
    /**
     * Creates the schema if missing and applies the tenant migrations.
     *
     * @param schemaName the schema, {@code gym_<id>}
     */
    void createTenantSchema(String schemaName);

    /**
     * Drops the schema and everything in it, if it exists.
     *
     * @param schemaName the schema, {@code gym_<id>}
     */
    void dropTenantSchema(String schemaName);
}
