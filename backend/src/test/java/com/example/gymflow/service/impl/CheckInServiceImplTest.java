package com.example.gymflow.service.impl;

import com.example.gymflow.dto.checkin.CheckInRequest;
import com.example.gymflow.entity.CheckIn;
import com.example.gymflow.entity.Member;
import com.example.gymflow.entity.Membership;
import com.example.gymflow.enums.CheckInDenialReason;
import com.example.gymflow.enums.CheckInMethod;
import com.example.gymflow.enums.CheckInResult;
import com.example.gymflow.mapper.CheckInMapperImpl;
import com.example.gymflow.mapper.MembershipMapperImpl;
import com.example.gymflow.repository.CheckInRepository;
import com.example.gymflow.repository.MemberRepository;
import com.example.gymflow.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckInServiceImpl")
class CheckInServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-03-10T19:00:00Z"); // 14:00 in Lima
    private static final UUID TOKEN = UUID.fromString("3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b");

    @Mock MemberRepository memberRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock CheckInRepository checkInRepository;

    CheckInServiceImpl service;
    Member member;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(NOW, ZoneId.of("America/Lima"));
        service = new CheckInServiceImpl(memberRepository, membershipRepository, checkInRepository,
                new MembershipMapperImpl(), new CheckInMapperImpl(), clock);
        member = Member.builder().id(3L).fullName("Luis Díaz").dni("12345678").qrToken(TOKEN).build();
    }

    private static Membership membership(String start, String end) {
        return Membership.builder().id(10L).planName("Mensual")
                .startDate(LocalDate.parse(start)).endDate(LocalDate.parse(end)).build();
    }

    private void givenMemberships(Membership... memberships) {
        when(memberRepository.findByQrToken(TOKEN)).thenReturn(Optional.of(member));
        when(membershipRepository.findAllByMemberIdWithPayment(3L)).thenReturn(List.of(memberships));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GF1:not-a-uuid", "ab", "12345678901234", "hola mundo"})
    void checkIn_should_denyInvalidCode_withoutRecording(String code) {
        var response = service.checkIn(new CheckInRequest(code));

        assertThat(response.result()).isEqualTo(CheckInResult.DENIED);
        assertThat(response.reason()).isEqualTo(CheckInDenialReason.INVALID_CODE);
        assertThat(response.member()).isNull();
        verifyNoInteractions(checkInRepository);
    }

    @Test
    void checkIn_should_denyUnknown_when_tokenDoesNotMatchAMember() {
        when(memberRepository.findByQrToken(TOKEN)).thenReturn(Optional.empty());

        var response = service.checkIn(new CheckInRequest("GF1:" + TOKEN));

        assertThat(response.reason()).isEqualTo(CheckInDenialReason.UNKNOWN);
        assertThat(response.checkedAt()).isEqualTo(NOW);
        verifyNoInteractions(checkInRepository);
    }

    @Test
    void checkIn_should_acceptBareUuid_asQr() {
        givenMemberships(membership("2026-03-01", "2026-03-30"));
        when(checkInRepository.findFirstByMemberIdAndResultAndCheckedAtAfterOrderByCheckedAtAsc(any(), any(), any()))
                .thenReturn(Optional.empty());

        var response = service.checkIn(new CheckInRequest(TOKEN.toString()));

        assertThat(response.result()).isEqualTo(CheckInResult.ALLOWED);
        var saved = ArgumentCaptor.forClass(CheckIn.class);
        verify(checkInRepository).save(saved.capture());
        assertThat(saved.getValue().getMethod()).isEqualTo(CheckInMethod.QR);
    }

    @Test
    void checkIn_should_lookUpByDni_uppercased() {
        when(memberRepository.findByDni("AB12345")).thenReturn(Optional.of(member));
        when(membershipRepository.findAllByMemberIdWithPayment(3L)).thenReturn(List.of());

        var response = service.checkIn(new CheckInRequest(" ab12345 "));

        assertThat(response.reason()).isEqualTo(CheckInDenialReason.NO_MEMBERSHIP);
        var saved = ArgumentCaptor.forClass(CheckIn.class);
        verify(checkInRepository).save(saved.capture());
        assertThat(saved.getValue().getMethod()).isEqualTo(CheckInMethod.DNI);
        assertThat(saved.getValue().getResult()).isEqualTo(CheckInResult.DENIED);
    }

    @Test
    void checkIn_should_denyInactiveMember_beforeLookingAtMemberships() {
        member.setActive(false);
        when(memberRepository.findByQrToken(TOKEN)).thenReturn(Optional.of(member));

        var response = service.checkIn(new CheckInRequest("GF1:" + TOKEN));

        assertThat(response.reason()).isEqualTo(CheckInDenialReason.MEMBER_INACTIVE);
        assertThat(response.member().id()).isEqualTo(3L);
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void checkIn_should_denyNotStarted_when_onlyScheduled() {
        givenMemberships(membership("2026-03-15", "2026-04-13"));

        var response = service.checkIn(new CheckInRequest("GF1:" + TOKEN));

        assertThat(response.reason()).isEqualTo(CheckInDenialReason.NOT_STARTED);
        assertThat(response.membership().status().name()).isEqualTo("SCHEDULED");
    }

    @Test
    void checkIn_should_denyFrozen() {
        var frozen = membership("2026-03-01", "2026-03-30");
        frozen.setFrozenSince(LocalDate.of(2026, 3, 5));
        givenMemberships(frozen);

        assertThat(service.checkIn(new CheckInRequest("GF1:" + TOKEN)).reason()).isEqualTo(CheckInDenialReason.FROZEN);
    }

    @Test
    void checkIn_should_denyExpired_withZeroDaysLeft() {
        givenMemberships(membership("2026-02-08", "2026-03-09"));

        var response = service.checkIn(new CheckInRequest("GF1:" + TOKEN));

        assertThat(response.result()).isEqualTo(CheckInResult.DENIED);
        assertThat(response.reason()).isEqualTo(CheckInDenialReason.EXPIRED);
        assertThat(response.message()).isEqualTo("Su membresía venció");
        assertThat(response.membership().daysLeft()).isZero();
        assertThat(response.membership().endDate()).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void checkIn_should_allowAndRecord_when_activeAndNoRecentEntry() {
        givenMemberships(membership("2026-03-01", "2026-03-30"));
        when(checkInRepository.findFirstByMemberIdAndResultAndCheckedAtAfterOrderByCheckedAtAsc(
                eq(3L), eq(CheckInResult.ALLOWED), eq(NOW.minus(CheckInServiceImpl.DUPLICATE_WINDOW))))
                .thenReturn(Optional.empty());

        var response = service.checkIn(new CheckInRequest("GF1:" + TOKEN));

        assertThat(response.result()).isEqualTo(CheckInResult.ALLOWED);
        assertThat(response.duplicate()).isFalse();
        assertThat(response.reason()).isNull();
        assertThat(response.membership().daysLeft()).isEqualTo(20);
        assertThat(response.checkedAt()).isEqualTo(NOW);
        verify(checkInRepository).save(any(CheckIn.class));
    }

    @Test
    void checkIn_should_reportDuplicate_withFirstEntryTime_andNotRecordAgain() {
        givenMemberships(membership("2026-03-01", "2026-03-30"));
        var first = Instant.parse("2026-03-10T17:30:00Z");
        when(checkInRepository.findFirstByMemberIdAndResultAndCheckedAtAfterOrderByCheckedAtAsc(any(), any(), any()))
                .thenReturn(Optional.of(CheckIn.builder().checkedAt(first).build()));

        var response = service.checkIn(new CheckInRequest("GF1:" + TOKEN));

        assertThat(response.result()).isEqualTo(CheckInResult.ALLOWED);
        assertThat(response.duplicate()).isTrue();
        assertThat(response.checkedAt()).isEqualTo(first);
        verify(checkInRepository, never()).save(any());
    }
}
