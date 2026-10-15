package com.example.gymflow.config.tenant;

/**
 * Holds the tenant schema ({@code gym_<id>}) bound to the current thread.
 * Always cleared in a {@code finally} block because servlet threads are pooled.
 */
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final String SCHEMA_PREFIX = "gym_";

    private TenantContext() {}

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Builds the schema name of a gym.
     *
     * @param gymId the gym id
     * @return the tenant schema name
     */
    public static String schemaOf(Long gymId) {
        return SCHEMA_PREFIX + gymId;
    }

    /**
     * Returns the gym id of the tenant bound to this thread.
     *
     * @return the gym id
     * @throws IllegalStateException when no gym tenant is bound
     */
    public static Long currentGymId() {
        var tenant = CURRENT_TENANT.get();
        if (tenant == null || !tenant.startsWith(SCHEMA_PREFIX))
            throw new IllegalStateException("No gym tenant bound to the current thread");
        return Long.valueOf(tenant.substring(SCHEMA_PREFIX.length()));
    }
}
