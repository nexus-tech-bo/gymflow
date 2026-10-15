package com.example.gymflow.service.impl;

import com.example.gymflow.dto.membership.MembershipSaleRequest;
import com.example.gymflow.entity.*;
import com.example.gymflow.enums.CashSessionStatus;
import com.example.gymflow.enums.MembershipStatus;
import com.example.gymflow.enums.PaymentMethod;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.exception.ForbiddenException;
import com.example.gymflow.mapper.MembershipMapperImpl;
import com.example.gymflow.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MembershipServiceImpl")
class MembershipServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-03-10T19:00:00Z");
    private static final UUID KEY = UUID.fromString("6b1c0000-0000-4000-8000-000000000001");

    @Mock MembershipRepository membershipRepository;
    @Mock MemberRepository memberRepository;
    @Mock PlanRepository planRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock CashSessionRepository cashSessionRepository;
    @Mock StaffRepository staffRepository;

    MembershipServiceImpl service;
    Member member;
    Plan plan;

    @BeforeEach
    void setUp() {
        service = new MembershipServiceImpl(membershipRepository, memberRepository, planRepository, paymentRepository,
                cashSessionRepository, staffRepository, new MembershipMapperImpl(), Clock.fixed(NOW, ZoneId.of("America/Lima")));
        member = Member.builder().id(3L).fullName("Luis Díaz").build();
        plan = Plan.builder().id(2L).name("Mensual").durationDays(30).price(new BigDecimal("120.00")).build();
    }

    private MembershipSaleRequest sale(String price) {
        return new MembershipSaleRequest(2L, price == null ? null : new BigDecimal(price), PaymentMethod.QR, " OP-123 ", KEY);
    }

    private void givenSellable() {
        when(membershipRepository.findByPaymentIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(memberRepository.findLockedById(3L)).thenReturn(Optional.of(member));
        when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
    }

    @Test
    void sellMembership_should_throwConflict_when_noOpenCashSession() {
        givenSellable();
        when(cashSessionRepository.findSharedLockedByStatus(CashSessionStatus.OPEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sellMembership(3L, sale(null), 1L, "RECEPTIONIST"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No hay caja abierta");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void sellMembership_should_forbidPriceChange_forReceptionist() {
        givenSellable();

        assertThatThrownBy(() -> service.sellMembership(3L, sale("100.00"), 1L, "RECEPTIONIST"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sellMembership_should_throwConflict_when_memberInactive() {
        member.setActive(false);
        when(membershipRepository.findByPaymentIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(memberRepository.findLockedById(3L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.sellMembership(3L, sale(null), 1L, "OWNER"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sellMembership_should_queueRenewalAfterCurrent_andApplyOverridePrice_forAdmin() {
        givenSellable();
        when(cashSessionRepository.findSharedLockedByStatus(CashSessionStatus.OPEN))
                .thenReturn(Optional.of(CashSession.builder().id(7L).build()));
        var current = Membership.builder().startDate(LocalDate.of(2026, 3, 1)).endDate(LocalDate.of(2026, 3, 30)).build();
        when(membershipRepository.findAllByMemberIdWithPayment(3L)).thenReturn(List.of(current));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.sellMembership(3L, sale("100"), 1L, "ADMIN");

        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 4, 29));
        assertThat(response.status()).isEqualTo(MembershipStatus.SCHEDULED);
        assertThat(response.price()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.payment().amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.payment().method()).isEqualTo(PaymentMethod.QR);
    }

    @Test
    void sellMembership_should_throwConflict_when_memberHasFrozenMembership_evenPastItsEndDate() {
        givenSellable();
        var frozen = Membership.builder().id(10L)
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 30))
                .frozenSince(LocalDate.of(2026, 1, 20)).build();
        when(membershipRepository.findAllByMemberIdWithPayment(3L)).thenReturn(List.of(frozen));

        assertThatThrownBy(() -> service.sellMembership(3L, sale(null), 1L, "OWNER"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Descongele la membresía antes de renovar");
        verifyNoInteractions(cashSessionRepository, paymentRepository);
    }

    @Test
    void sellMembership_should_returnOriginalSale_when_idempotencyKeyReplayed() {
        var original = Membership.builder().id(10L).member(member).planName("Mensual")
                .startDate(LocalDate.of(2026, 3, 1)).endDate(LocalDate.of(2026, 3, 30)).build();
        when(memberRepository.findLockedById(3L)).thenReturn(Optional.of(member));
        when(membershipRepository.findByPaymentIdempotencyKey(KEY)).thenReturn(Optional.of(original));

        var response = service.sellMembership(3L, sale(null), 1L, "RECEPTIONIST");

        assertThat(response.id()).isEqualTo(10L);
        verifyNoInteractions(paymentRepository, cashSessionRepository);
    }

    @Test
    void sellMembership_should_throwConflict_when_idempotencyKeyBelongsToAnotherMember() {
        var other = Member.builder().id(99L).build();
        when(memberRepository.findLockedById(3L)).thenReturn(Optional.of(member));
        when(membershipRepository.findByPaymentIdempotencyKey(KEY))
                .thenReturn(Optional.of(Membership.builder().member(other).build()));

        assertThatThrownBy(() -> service.sellMembership(3L, sale(null), 1L, "OWNER"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unfreezeMembership_should_extendItAndQueuedRenewals_byFrozenDays() {
        var frozen = Membership.builder().id(10L).member(member)
                .startDate(LocalDate.of(2026, 3, 1)).endDate(LocalDate.of(2026, 3, 30))
                .frozenSince(LocalDate.of(2026, 3, 5)).build();
        var queued = Membership.builder().id(11L).member(member)
                .startDate(LocalDate.of(2026, 3, 31)).endDate(LocalDate.of(2026, 4, 29)).build();
        when(membershipRepository.findWithPaymentById(10L)).thenReturn(Optional.of(frozen));
        when(membershipRepository.findAllByMemberIdAndCancelledAtIsNullAndStartDateAfter(3L, LocalDate.of(2026, 3, 1)))
                .thenReturn(List.of(queued));
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.unfreezeMembershipById(10L);

        assertThat(response.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 4, 4));
        assertThat(response.frozenDays()).isEqualTo(5);
        assertThat(response.frozenSince()).isNull();
        assertThat(queued.getStartDate()).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(queued.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 4));
    }

    @Test
    void freezeMembership_should_throwConflict_when_notActive() {
        var scheduled = Membership.builder().id(10L)
                .startDate(LocalDate.of(2026, 4, 1)).endDate(LocalDate.of(2026, 4, 30)).build();
        when(membershipRepository.findWithPaymentById(10L)).thenReturn(Optional.of(scheduled));

        assertThatThrownBy(() -> service.freezeMembershipById(10L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void cancelMembership_should_throwConflict_when_alreadyCancelled() {
        var cancelled = Membership.builder().id(10L).cancelledAt(NOW)
                .startDate(LocalDate.of(2026, 3, 1)).endDate(LocalDate.of(2026, 3, 30)).build();
        when(membershipRepository.findWithPaymentById(10L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.cancelMembershipById(10L)).isInstanceOf(BusinessException.class);
    }
}
