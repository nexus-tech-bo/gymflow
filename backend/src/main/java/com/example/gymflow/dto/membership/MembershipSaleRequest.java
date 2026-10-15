package com.example.gymflow.dto.membership;

import com.example.gymflow.enums.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sells or renews a membership; {@code price} overrides the plan price (OWNER/ADMIN only).
 */
public record MembershipSaleRequest(
        @NotNull
        Long planId,
        @DecimalMin("0.00")
        @DecimalMax("99999.99")
        @Digits(integer = 5, fraction = 2)
        BigDecimal price,
        @NotNull
        PaymentMethod paymentMethod,
        @Size(max = 40)
        String paymentReference,
        @NotNull
        UUID idempotencyKey
) {}
