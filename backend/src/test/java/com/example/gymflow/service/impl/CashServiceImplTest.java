package com.example.gymflow.service.impl;

import com.example.gymflow.dto.cash.CashCloseRequest;
import com.example.gymflow.dto.cash.CashOpenRequest;
import com.example.gymflow.entity.*;
import com.example.gymflow.enums.CashSessionStatus;
import com.example.gymflow.enums.PaymentMethod;
import com.example.gymflow.enums.Role;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.mapper.CashSessionMapperImpl;
import com.example.gymflow.mapper.PaymentMapperImpl;
import com.example.gymflow.repository.CashSessionRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CashServiceImpl")
class CashServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-03-10T22:00:00Z");

    @Mock CashSessionRepository cashSessionRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock StaffRepository staffRepository;

    CashServiceImpl service;
    Staff ana;
    CashSession open;

    @BeforeEach
    void setUp() {
        service = new CashServiceImpl(cashSessionRepository, paymentRepository, staffRepository,
                new CashSessionMapperImpl(), new PaymentMapperImpl(), Clock.fixed(NOW, ZoneId.of("America/Lima")));
        ana = Staff.builder().id(1L).role(Role.OWNER).account(Account.builder().fullName("Ana Pérez").build()).build();
        open = CashSession.builder().id(7L).status(CashSessionStatus.OPEN).openedBy(ana)
                .openedAt(Instant.parse("2026-03-10T12:00:00Z")).openingAmount(new BigDecimal("100.00")).build();
    }

    private Payment payment(PaymentMethod method, String amount, boolean voided) {
        return Payment.builder().id(1L).method(method).amount(new BigDecimal(amount)).voided(voided)
                .member(Member.builder().id(3L).fullName("Luis Díaz").build()).receivedBy(ana).planName("Mensual")
                .paidAt(NOW).build();
    }

    private void givenOpenSessionWithPayments() {
        when(cashSessionRepository.findLockedByStatus(CashSessionStatus.OPEN)).thenReturn(Optional.of(open));
        when(staffRepository.findById(1L)).thenReturn(Optional.of(ana));
        when(cashSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findAllByCashSessionId(7L)).thenReturn(List.of(
                payment(PaymentMethod.CASH, "120.00", false),
                payment(PaymentMethod.QR, "50.00", false),
                payment(PaymentMethod.CASH, "80.00", true)));
    }

    @Test
    void closeCashSession_should_computeTotalsExpectedAndDifference_excludingVoided_forOwner() {
        givenOpenSessionWithPayments();

        var detail = service.closeCashSession(new CashCloseRequest(new BigDecimal("215"), " Faltaron 5 bolivianos "), 1L, "OWNER");

        var session = detail.session();
        assertThat(session.status()).isEqualTo(CashSessionStatus.CLOSED);
        assertThat(session.closedAt()).isEqualTo(NOW);
        assertThat(session.closedByName()).isEqualTo("Ana Pérez");
        assertThat(session.notes()).isEqualTo("Faltaron 5 bolivianos");
        assertThat(session.totals().cash()).isEqualByComparingTo("120.00");
        assertThat(session.totals().qr()).isEqualByComparingTo("50.00");
        assertThat(session.totals().total()).isEqualByComparingTo("170.00");
        assertThat(session.totals().count()).isEqualTo(2);
        assertThat(session.expectedCash()).isEqualByComparingTo("220.00");
        assertThat(session.countedCash()).isEqualTo(new BigDecimal("215.00"));
        assertThat(session.difference()).isEqualByComparingTo("-5.00");
        assertThat(detail.payments()).hasSize(3);
    }

    @Test
    void closeCashSession_should_hideExpectedTotalsAndDifference_forReceptionist() {
        givenOpenSessionWithPayments();

        var session = service.closeCashSession(new CashCloseRequest(new BigDecimal("215.00"), null), 1L, "RECEPTIONIST").session();

        assertThat(session.expectedCash()).isNull();
        assertThat(session.totals()).isNull();
        assertThat(session.difference()).isNull();
        assertThat(session.countedCash()).isEqualByComparingTo("215.00");
        assertThat(session.status()).isEqualTo(CashSessionStatus.CLOSED);
    }

    @Test
    void closeCashSession_should_throwConflict_when_noOpenSession() {
        when(cashSessionRepository.findLockedByStatus(CashSessionStatus.OPEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.closeCashSession(new CashCloseRequest(BigDecimal.TEN, null), 1L, "OWNER"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No hay caja abierta");
        verify(cashSessionRepository, never()).save(any());
    }

    @Test
    void openCashSession_should_throwConflict_when_alreadyOpen() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> service.openCashSession(new CashOpenRequest(BigDecimal.TEN), 1L, "OWNER"))
                .isInstanceOf(BusinessException.class);
        verify(cashSessionRepository, never()).save(any());
    }

    @Test
    void findCurrentCashSession_should_returnNullCurrent_when_closed() {
        when(cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN)).thenReturn(Optional.empty());

        assertThat(service.findCurrentCashSession("OWNER").current()).isNull();
    }

    @Test
    void findCurrentCashSession_should_showExpectedCashWithoutDifference_whileOpen() {
        when(cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN)).thenReturn(Optional.of(open));
        when(paymentRepository.findAllByCashSessionId(7L)).thenReturn(List.of(payment(PaymentMethod.CASH, "120.00", false)));

        var session = service.findCurrentCashSession("ADMIN").current().session();

        assertThat(session.expectedCash()).isEqualByComparingTo("220.00");
        assertThat(session.difference()).isNull();
        assertThat(session.closedAt()).isNull();
    }
}
