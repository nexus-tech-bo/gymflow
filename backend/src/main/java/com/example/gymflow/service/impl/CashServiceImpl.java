package com.example.gymflow.service.impl;

import com.example.gymflow.dto.cash.CashCloseRequest;
import com.example.gymflow.dto.cash.CashCurrentResponse;
import com.example.gymflow.dto.cash.CashOpenRequest;
import com.example.gymflow.dto.cash.CashSessionDetailResponse;
import com.example.gymflow.dto.cash.CashSessionResponse;
import com.example.gymflow.dto.payment.PaymentTotals;
import com.example.gymflow.entity.CashSession;
import com.example.gymflow.entity.Staff;
import com.example.gymflow.enums.CashSessionStatus;
import com.example.gymflow.enums.Role;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.exception.ResourceNotFoundException;
import com.example.gymflow.mapper.CashSessionMapper;
import com.example.gymflow.mapper.PaymentMapper;
import com.example.gymflow.repository.CashSessionRepository;
import com.example.gymflow.repository.PaymentRepository;
import com.example.gymflow.repository.StaffRepository;
import com.example.gymflow.repository.projection.MethodTotal;
import com.example.gymflow.repository.projection.SessionMethodTotal;
import com.example.gymflow.service.CashService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashServiceImpl implements CashService {
    private static final PaymentTotals NO_PAYMENTS = PaymentTotals.of(List.of());

    private final CashSessionRepository cashSessionRepository;
    private final PaymentRepository paymentRepository;
    private final StaffRepository staffRepository;
    private final CashSessionMapper cashSessionMapper;
    private final PaymentMapper paymentMapper;
    private final Clock clock;

    @Override
    public CashCurrentResponse findCurrentCashSession(String callerRole) {
        return new CashCurrentResponse(cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN)
                .map(session -> toDetailResponse(session, isBlind(callerRole)))
                .orElse(null));
    }

    @Override
    @Transactional
    public CashSessionDetailResponse openCashSession(CashOpenRequest request, Long staffId, String callerRole) {
        if (cashSessionRepository.existsByStatus(CashSessionStatus.OPEN))
            throw new BusinessException("Ya hay una caja abierta");
        var session = cashSessionRepository.save(CashSession.builder()
                .openedAt(Instant.now(clock))
                .openedBy(getStaffOrThrowById(staffId))
                .openingAmount(request.openingAmount().setScale(2, RoundingMode.HALF_UP))
                .build());
        return toDetailResponse(session, isBlind(callerRole));
    }

    @Override
    @Transactional
    public CashSessionDetailResponse closeCashSession(CashCloseRequest request, Long staffId, String callerRole) {
        // exclusive lock: waits for in-flight sales/voids (shared locks) so the totals below are final
        var session = cashSessionRepository.findLockedByStatus(CashSessionStatus.OPEN)
                .orElseThrow(() -> new BusinessException("No hay caja abierta"));
        session.setStatus(CashSessionStatus.CLOSED);
        session.setClosedAt(Instant.now(clock));
        session.setClosedBy(getStaffOrThrowById(staffId));
        session.setCountedCash(request.countedCash().setScale(2, RoundingMode.HALF_UP));
        session.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        return toDetailResponse(cashSessionRepository.save(session), isBlind(callerRole));
    }

    @Override
    public Page<CashSessionResponse> findAllCashSessions(Pageable pageable) {
        var sessions = cashSessionRepository.findAll(pageable);
        var ids = sessions.map(CashSession::getId).getContent();
        var totalsBySession = ids.isEmpty()
                ? Map.<Long, PaymentTotals>of()
                : paymentRepository.sumBySessionAndMethod(ids).stream()
                        .collect(Collectors.groupingBy(SessionMethodTotal::sessionId,
                                Collectors.collectingAndThen(
                                        Collectors.mapping(SessionMethodTotal::toMethodTotal, Collectors.toList()),
                                        PaymentTotals::of)));
        return sessions.map(s -> toResponse(s, totalsBySession.getOrDefault(s.getId(), NO_PAYMENTS), false));
    }

    @Override
    public CashSessionDetailResponse findCashSessionById(Long cashSessionId) {
        var session = cashSessionRepository.findById(cashSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Caja no encontrada con id: " + cashSessionId));
        return toDetailResponse(session, false);
    }

    private CashSessionDetailResponse toDetailResponse(CashSession session, boolean blind) {
        var payments = paymentRepository.findAllByCashSessionId(session.getId());
        var totals = PaymentTotals.of(payments.stream()
                .filter(p -> !p.isVoided())
                .map(p -> new MethodTotal(p.getMethod(), p.getAmount(), 1L))
                .toList());
        return new CashSessionDetailResponse(toResponse(session, totals, blind), paymentMapper.toResponseList(payments));
    }

    /**
     * Expected cash = opening amount + non-voided cash payments; difference = counted - expected once closed.
     * A blind view hides all three.
     */
    private CashSessionResponse toResponse(CashSession session, PaymentTotals totals, boolean blind) {
        if (blind)
            return cashSessionMapper.toResponse(session, null, null, null);
        var expectedCash = session.getOpeningAmount().add(totals.cash());
        var difference = session.getCountedCash() == null ? null : session.getCountedCash().subtract(expectedCash);
        return cashSessionMapper.toResponse(session, totals, expectedCash, difference);
    }

    private static boolean isBlind(String callerRole) {
        return Role.RECEPTIONIST.name().equals(callerRole);
    }

    private Staff getStaffOrThrowById(Long staffId) {
        return staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + staffId));
    }
}
