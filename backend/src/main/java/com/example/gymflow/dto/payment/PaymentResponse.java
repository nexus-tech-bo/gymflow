package com.example.gymflow.dto.payment;

import com.example.gymflow.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment.
 */
public record PaymentResponse(
        Long id,
        Long memberId,
        String memberName,
        String planName,
        BigDecimal amount,
        PaymentMethod method,
        String reference,
        String receivedByName,
        Instant paidAt,
        boolean voided,
        String voidReason
) {}
