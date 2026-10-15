package com.example.gymflow.dto.membership;

import com.example.gymflow.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment summary embedded in a membership.
 */
public record MembershipPaymentResponse(
        Long id,
        PaymentMethod method,
        BigDecimal amount,
        Instant paidAt,
        boolean voided
) {}
