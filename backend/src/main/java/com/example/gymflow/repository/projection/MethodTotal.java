package com.example.gymflow.repository.projection;

import com.example.gymflow.enums.PaymentMethod;

import java.math.BigDecimal;

/**
 * Sum and count of non-voided payments for one method.
 */
public record MethodTotal(PaymentMethod method, BigDecimal amount, Long count) {}
