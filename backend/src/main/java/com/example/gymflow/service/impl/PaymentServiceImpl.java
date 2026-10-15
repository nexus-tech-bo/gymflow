package com.example.gymflow.service.impl;

import com.example.gymflow.dto.payment.PaymentResponse;
import com.example.gymflow.dto.payment.PaymentVoidRequest;
import com.example.gymflow.enums.CashSessionStatus;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.exception.ResourceNotFoundException;
import com.example.gymflow.mapper.PaymentMapper;
import com.example.gymflow.repository.CashSessionRepository;
import com.example.gymflow.repository.MembershipRepository;
import com.example.gymflow.repository.PaymentRepository;
import com.example.gymflow.repository.StaffRepository;
import com.example.gymflow.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final CashSessionRepository cashSessionRepository;
    private final MembershipRepository membershipRepository;
    private final StaffRepository staffRepository;
    private final PaymentMapper paymentMapper;
    private final Clock clock;

    @Override
    @Transactional
    public PaymentResponse voidPaymentById(Long paymentId, PaymentVoidRequest request, Long staffId) {
        var payment = paymentRepository.findWithDetailsById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado con id: " + paymentId));
        if (payment.isVoided())
            throw new BusinessException("El pago ya fue anulado");
        // shared lock: a concurrent close waits for this void, or this void sees the session closed
        cashSessionRepository.findSharedLockedByIdAndStatus(payment.getCashSession().getId(), CashSessionStatus.OPEN)
                .orElseThrow(() -> new BusinessException("Solo se pueden anular pagos de la caja abierta"));
        var now = Instant.now(clock);
        payment.setVoided(true);
        payment.setVoidReason(request.reason().trim());
        payment.setVoidedAt(now);
        payment.setVoidedBy(staffRepository.getReferenceById(staffId));
        membershipRepository.findByPaymentId(payment.getId())
                .filter(m -> m.getCancelledAt() == null)
                .ifPresent(m -> m.setCancelledAt(now));
        return paymentMapper.toResponse(paymentRepository.save(payment));
    }
}
