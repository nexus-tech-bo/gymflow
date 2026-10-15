package com.example.gymflow.dto.cash;

import com.example.gymflow.dto.payment.PaymentTotals;
import com.example.gymflow.enums.CashSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A cash session. {@code expectedCash}, {@code totals} and {@code difference} are {@code null} for a blind
 * close (RECEPTIONIST); closing fields are {@code null} while open.
 */
public record CashSessionResponse(
        Long id,
        CashSessionStatus status,
        Instant openedAt,
        String openedByName,
        BigDecimal openingAmount,
        BigDecimal expectedCash,
        PaymentTotals totals,
        Instant closedAt,
        String closedByName,
        BigDecimal countedCash,
        BigDecimal difference,
        String notes
) {}
