package com.example.gymflow.service;

import com.example.gymflow.dto.payment.PaymentResponse;
import com.example.gymflow.dto.payment.PaymentVoidRequest;

/**
 * Payment corrections.
 */
public interface PaymentService {
    /**
     * Voids a payment of the open cash session and cancels its membership.
     *
     * @param paymentId the payment id
     * @param request   the reason
     * @param staffId   the authenticated staff id
     * @return the voided payment
     * @throws com.example.gymflow.exception.BusinessException when already voided or its session is closed
     */
    PaymentResponse voidPaymentById(Long paymentId, PaymentVoidRequest request, Long staffId);
}
