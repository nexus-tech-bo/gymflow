package com.example.gymflow.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Staff credentials.
 */
public record LoginRequest(
        @NotBlank
        @Email
        String email,
        @NotBlank
        String password
) {}
