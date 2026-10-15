package com.example.gymflow.dto.auth;

/**
 * Access token, its lifetime in seconds and the authenticated user.
 */
public record AuthResponse(
        String accessToken,
        long expiresIn,
        MeResponse user
) {}
