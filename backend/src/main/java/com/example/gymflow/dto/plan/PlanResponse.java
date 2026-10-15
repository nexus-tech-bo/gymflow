package com.example.gymflow.dto.plan;

import java.math.BigDecimal;

/**
 * A membership plan.
 */
public record PlanResponse(
        Long id,
        String name,
        int durationDays,
        BigDecimal price,
        boolean active
) {}
