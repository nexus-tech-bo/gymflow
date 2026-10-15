package com.example.gymflow.dto.auth;

import com.example.gymflow.enums.Role;

/**
 * The authenticated staff user and its gym.
 */
public record MeResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        Long gymId,
        String gymName
) {}
