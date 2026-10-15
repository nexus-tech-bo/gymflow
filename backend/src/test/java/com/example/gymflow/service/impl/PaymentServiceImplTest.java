package com.example.gymflow.service.impl;

import com.example.gymflow.dto.payment.PaymentVoidRequest;
import com.example.gymflow.entity.*;
import com.example.gymflow.enums.CashSessionStatus;
import com.example.gymflow.enums.PaymentMethod;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.mapper.PaymentMapperImpl;
import com.example.gymflow.repository.CashSessionRepository;
import com.example.gymflow.repository.MembershipRepository;
import com.example.gymflow.repository.PaymentRepository;
import com.example.gymflow.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl")
class PaymentServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-03-10T20:00:00Z");

    @Mock PaymentRepository paymentRepository;
    @Mock CashSessionRepository cashSessionRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock StaffRepository staffRepository;

    PaymentServiceImpl service;
    Payment payment;
    CashSession session;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, cashSessionRepository, membershipRepository, staffRepository,
                new PaymentMapperImpl(), Clock.fixed(NOW, ZoneId.of("America/Lima")));
        session = CashSession.builder().id(7L).status(CashSessionStatus.OPEN).build();
        var ana = Staff.builder().id(1L).account(Account.builder().fullName("Ana Pérez").build()).build();
        payment = Payment.builder().id(5L).cashSession(session).amount(new BigDecimal("120.00")).method(PaymentMethod.CASH)
                .planName("Mensual").member(Member.builder().id(3L).fullName("Luis Díaz").build()).receivedBy(ana).build();
    }

    @Test
    void voidPayment_should_markVoided_andCancelItsMembership() {
        var membership = Membership.builder().id(10L).build();
        when(paymentRepository.findWithDetailsById(5L)).thenReturn(Optional.of(payment));
        when(cashSessionRepository.findSharedLockedByIdAndStatus(7L, CashSessionStatus.OPEN)).thenReturn(Optional.of(session));
        when(membershipRepository.findByPaymentId(5L)).thenReturn(Optional.of(membership));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.voidPaymentById(5L, new PaymentVoidRequest(" Cobro duplicado "), 1L);

        assertThat(response.voided()).isTrue();
        assertThat(response.voidReason()).isEqualTo("Cobro duplicado");
        assertThat(payment.getVoidedAt()).isEqualTo(NOW);
        assertThat(membership.getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    void voidPayment_should_throwConflict_when_alreadyVoided() {
        payment.setVoided(true);
        when(paymentRepository.findWithDetailsById(5L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.voidPaymentById(5L, new PaymentVoidRequest("x"), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El pago ya fue anulado");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void voidPayment_should_throwConflict_when_sessionNoLongerOpen() {
        when(paymentRepository.findWithDetailsById(5L)).thenReturn(Optional.of(payment));
        when(cashSessionRepository.findSharedLockedByIdAndStatus(7L, CashSessionStatus.OPEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.voidPaymentById(5L, new PaymentVoidRequest("x"), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Solo se pueden anular pagos de la caja abierta");
        assertThat(payment.isVoided()).isFalse();
        verifyNoInteractions(membershipRepository);
    }
}
