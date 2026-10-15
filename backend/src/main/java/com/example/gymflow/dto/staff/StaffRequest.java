package com.example.gymflow.dto.staff;

import com.example.gymflow.enums.Role;
import com.example.gymflow.validator.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creates a staff user.
 */
public record StaffRequest(
        @NotBlank
        @Size(max = 120)
        String fullName,
        @NotBlank
        @Email
        @Size(max = 255)
        String email,
        @NotBlank
        @Size(min = 8)
        @MaxBytes(72)
        String password,
        @NotNull
        Role role
) {}
