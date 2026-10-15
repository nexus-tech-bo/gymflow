package com.example.gymflow.dto.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Creates or replaces a member.
 */
public record MemberRequest(
        @NotBlank
        @Size(max = 120)
        String fullName,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9]{6,12}$", message = "debe tener de 6 a 12 letras o números")
        String dni,
        @Size(max = 20)
        String phone,
        @Email
        @Size(max = 255)
        String email,
        @Past
        LocalDate birthDate,
        @Size(max = 500)
        String notes
) {}
