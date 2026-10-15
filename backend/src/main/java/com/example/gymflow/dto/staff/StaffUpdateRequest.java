package com.example.gymflow.dto.staff;

import com.example.gymflow.enums.Role;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial staff update: {@code null} fields are left unchanged.
 */
public record StaffUpdateRequest(
        @Size(max = 120)
        @Pattern(regexp = ".*\\S.*", message = "no debe estar vacío")
        String fullName,
        Role role,
        Boolean active
) {}
