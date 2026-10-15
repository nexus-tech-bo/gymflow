package com.example.gymflow.dto.staff;

import com.example.gymflow.enums.Role;

import java.time.Instant;

/**
 * A staff user.
 */
public record StaffResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        boolean active,
        Instant createdAt
) {}
