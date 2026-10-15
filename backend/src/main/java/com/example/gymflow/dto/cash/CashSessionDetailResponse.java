package com.example.gymflow.dto.cash;

import com.example.gymflow.dto.payment.PaymentResponse;

import java.util.List;

/**
 * A cash session with its payments (voided ones included).
 */
public record CashSessionDetailResponse(
        CashSessionResponse session,
        List<PaymentResponse> payments
) {}
