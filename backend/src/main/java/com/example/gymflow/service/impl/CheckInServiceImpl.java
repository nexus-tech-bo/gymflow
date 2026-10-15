package com.example.gymflow.service.impl;

import com.example.gymflow.dto.checkin.CheckInEntryResponse;
import com.example.gymflow.dto.checkin.CheckInMemberResponse;
import com.example.gymflow.dto.checkin.CheckInMembershipResponse;
import com.example.gymflow.dto.checkin.CheckInRequest;
import com.example.gymflow.dto.checkin.CheckInResponse;
import com.example.gymflow.dto.member.MemberQrResponse;
import com.example.gymflow.entity.CheckIn;
import com.example.gymflow.entity.Member;
import com.example.gymflow.enums.CheckInDenialReason;
import com.example.gymflow.enums.CheckInMethod;
import com.example.gymflow.enums.CheckInResult;
import com.example.gymflow.mapper.CheckInMapper;
import com.example.gymflow.mapper.MembershipMapper;
import com.example.gymflow.repository.CheckInRepository;
import com.example.gymflow.repository.MemberRepository;
import com.example.gymflow.repository.MembershipRepository;
import com.example.gymflow.service.CheckInService;
import com.example.gymflow.service.support.MembershipRules;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Check-in decision. Attempts with an identified member are recorded (allowed or denied); invalid codes,
 * unknown members and duplicates within {@link #DUPLICATE_WINDOW} are not.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckInServiceImpl implements CheckInService {
    static final Duration DUPLICATE_WINDOW = Duration.ofHours(2);
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
    private static final Pattern DNI_PATTERN = Pattern.compile("^[A-Za-z0-9]{6,12}$");

    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final CheckInRepository checkInRepository;
    private final MembershipMapper membershipMapper;
    private final CheckInMapper checkInMapper;
    private final Clock clock;

    @Override
    @Transactional
    public CheckInResponse checkIn(CheckInRequest request) {
        var now = Instant.now(clock);
        var today = LocalDate.now(clock);
        var code = request.code().trim();

        CheckInMethod method;
        Optional<Member> found;
        var hasPrefix = code.regionMatches(true, 0, MemberQrResponse.PAYLOAD_PREFIX, 0, MemberQrResponse.PAYLOAD_PREFIX.length());
        var token = hasPrefix ? code.substring(MemberQrResponse.PAYLOAD_PREFIX.length()) : code;
        if (UUID_PATTERN.matcher(token).matches()) {
            method = CheckInMethod.QR;
            found = memberRepository.findByQrToken(UUID.fromString(token));
        } else if (!hasPrefix && DNI_PATTERN.matcher(code).matches()) {
            method = CheckInMethod.DNI;
            found = memberRepository.findByDni(code.toUpperCase(Locale.ROOT));
        } else {
            return denied(CheckInDenialReason.INVALID_CODE, now);
        }
        if (found.isEmpty())
            return denied(CheckInDenialReason.UNKNOWN, now);

        var member = found.get();
        var memberResponse = new CheckInMemberResponse(member.getId(), member.getFullName());
        if (!member.isActive())
            return record(member, method, CheckInDenialReason.MEMBER_INACTIVE, memberResponse, null, now);

        var current = MembershipRules.pickCurrent(membershipRepository.findAllByMemberIdWithPayment(member.getId()), today);
        if (current.isEmpty())
            return record(member, method, CheckInDenialReason.NO_MEMBERSHIP, memberResponse, null, now);

        var membership = current.get();
        var membershipResponse = membershipMapper.toCheckInResponse(membership, today);
        var reason = switch (MembershipRules.effectiveStatus(membership, today)) {
            case ACTIVE -> null;
            case SCHEDULED -> CheckInDenialReason.NOT_STARTED;
            case FROZEN -> CheckInDenialReason.FROZEN;
            case EXPIRED, CANCELLED -> CheckInDenialReason.EXPIRED;
        };
        if (reason != null)
            return record(member, method, reason, memberResponse, membershipResponse, now);

        var previous = checkInRepository.findFirstByMemberIdAndResultAndCheckedAtAfterOrderByCheckedAtAsc(
                member.getId(), CheckInResult.ALLOWED, now.minus(DUPLICATE_WINDOW));
        return previous.map(checkIn -> new CheckInResponse(CheckInResult.ALLOWED, true, null, "Ya registró su ingreso",
                memberResponse, membershipResponse, checkIn.getCheckedAt())).orElseGet(() -> record(member, method, null, memberResponse, membershipResponse, now));
    }

    @Override
    public Page<CheckInEntryResponse> findAllCheckIns(LocalDate date, Pageable pageable) {
        var day = date != null ? date : LocalDate.now(clock);
        var zone = clock.getZone();
        return checkInRepository.findAllBetween(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant(), pageable)
                .map(checkInMapper::toEntryResponse);
    }

    private CheckInResponse record(Member member, CheckInMethod method, CheckInDenialReason reason,
                                   CheckInMemberResponse memberResponse, CheckInMembershipResponse membershipResponse, Instant now) {
        var result = reason == null ? CheckInResult.ALLOWED : CheckInResult.DENIED;
        checkInRepository.save(CheckIn.builder()
                .member(member)
                .method(method)
                .result(result)
                .reason(reason)
                .checkedAt(now)
                .build());
        return new CheckInResponse(result, false, reason, message(reason), memberResponse, membershipResponse, now);
    }

    private static CheckInResponse denied(CheckInDenialReason reason,
                                          Instant now) {
        return new CheckInResponse(CheckInResult.DENIED, false, reason, message(reason), null, null, now);
    }

    private static String message(CheckInDenialReason reason) {
        return switch (reason) {
            case null -> "Bienvenido";
            case INVALID_CODE -> "Código inválido";
            case UNKNOWN -> "Socio no encontrado";
            case MEMBER_INACTIVE -> "El socio está inactivo";
            case NO_MEMBERSHIP -> "No tiene membresía";
            case NOT_STARTED -> "Su membresía aún no inicia";
            case FROZEN -> "Su membresía está congelada";
            case EXPIRED -> "Su membresía venció";
        };
    }
}
