package com.example.gymflow.dto.auth;

import com.example.gymflow.validator.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registers a gym together with its OWNER.
 */
public record RegisterGymRequest(
        @NotBlank
        @Size(max = 120)
        String gymName,
        @NotBlank
        @Size(max = 120)
        String ownerName,
        @NotBlank
        @Email
        @Size(max = 255)
        String email,
        @NotBlank
        @Size(min = 8)
        @MaxBytes(72)
        String password
) {}
