package com.example.gymflow.dto.plan;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Creates or updates a plan; {@code active} is ignored on creation and left unchanged when {@code null}.
 */
public record PlanRequest(
        @NotBlank
        @Size(max = 60)
        String name,
        @NotNull
        @Min(1)
        @Max(730)
        Integer durationDays,
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("99999.99")
        @Digits(integer = 5, fraction = 2)
        BigDecimal price,
        Boolean active
) {}
