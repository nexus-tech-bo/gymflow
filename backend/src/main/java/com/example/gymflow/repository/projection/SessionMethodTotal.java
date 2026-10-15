package com.example.gymflow.repository.projection;

import com.example.gymflow.enums.PaymentMethod;

import java.math.BigDecimal;

/**
 * Sum and count of non-voided payments for one cash session and method.
 */
public record SessionMethodTotal(Long sessionId, PaymentMethod method, BigDecimal amount, Long count) {
    public MethodTotal toMethodTotal() {
        return new MethodTotal(method, amount, count);
    }
}
