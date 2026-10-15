package com.example.gymflow.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Instant and amount of a non-voided payment, for income-by-day grouping.
 */
public record PaymentLine(Instant paidAt, BigDecimal amount) {}
