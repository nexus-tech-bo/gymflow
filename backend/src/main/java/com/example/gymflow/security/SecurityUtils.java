package com.example.gymflow.security;

import org.springframework.security.core.Authentication;

import java.util.Objects;

/**
 * Helpers to read the authenticated staff user from {@link Authentication}.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the role without the {@code ROLE_} prefix.
     *
     * @param auth the current authentication
     * @return the role name, or {@code null} when absent
     */
    public static String extractRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(a -> Objects.requireNonNull(a.getAuthority()).replace("ROLE_", ""))
                .findFirst()
                .orElse(null);
    }
}
