package com.example.gymflow.service.impl;

import com.example.gymflow.dto.membership.MembershipResponse;
import com.example.gymflow.dto.membership.MembershipSaleRequest;
import com.example.gymflow.entity.Membership;
import com.example.gymflow.entity.Payment;
import com.example.gymflow.enums.CashSessionStatus;
import com.example.gymflow.enums.MembershipStatus;
import com.example.gymflow.enums.Role;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.exception.ForbiddenException;
import com.example.gymflow.exception.ResourceNotFoundException;
import com.example.gymflow.mapper.MembershipMapper;
import com.example.gymflow.repository.*;
import com.example.gymflow.service.MembershipService;
import com.example.gymflow.service.support.MembershipRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipServiceImpl implements MembershipService {
    private final MembershipRepository membershipRepository;
    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final CashSessionRepository cashSessionRepository;
    private final StaffRepository staffRepository;
    private final MembershipMapper membershipMapper;
    private final Clock clock;

    @Override
    @Transactional
    public MembershipResponse sellMembership(Long memberId, MembershipSaleRequest request, Long staffId, String callerRole) {
        var today = LocalDate.now(clock);
        // lock the member first: sales of one member run one at a time, so a retry with the same key replays
        var member = memberRepository.findLockedById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Socio no encontrado con id: " + memberId));
        var replay = membershipRepository.findByPaymentIdempotencyKey(request.idempotencyKey());
        if (replay.isPresent()) {
            if (!replay.get().getMember().getId().equals(memberId))
                throw new BusinessException("La clave de idempotencia ya se usó en otra venta");
            return membershipMapper.toResponse(replay.get(), today);
        }
        if (!member.isActive())
            throw new BusinessException("El socio está inactivo");
        var plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado con id: " + request.planId()));
        if (!plan.isActive())
            throw new BusinessException("El plan está inactivo");
        var price = plan.getPrice();
        if (request.price() != null && request.price().compareTo(plan.getPrice()) != 0) {
            if (Role.RECEPTIONIST.name().equals(callerRole))
                throw new ForbiddenException("Solo OWNER o ADMIN pueden cambiar el precio");
            price = request.price();
        }
        price = price.setScale(2, RoundingMode.HALF_UP);
        var memberships = membershipRepository.findAllByMemberIdWithPayment(memberId);
        // a frozen membership has no final end date yet, so a renewal cannot be queued after it
        if (memberships.stream().anyMatch(m -> MembershipRules.effectiveStatus(m, today) == MembershipStatus.FROZEN))
            throw new BusinessException("Descongele la membresía antes de renovar");
        var cashSession = cashSessionRepository.findSharedLockedByStatus(CashSessionStatus.OPEN)
                .orElseThrow(() -> new BusinessException("No hay caja abierta"));
        var startDate = MembershipRules.nextStartDate(memberships, today);
        var payment = paymentRepository.save(Payment.builder()
                .member(member)
                .cashSession(cashSession)
                .planName(plan.getName())
                .amount(price)
                .method(request.paymentMethod())
                .reference(blankToNull(request.paymentReference()))
                .receivedBy(staffRepository.getReferenceById(staffId))
                .paidAt(Instant.now(clock))
                .idempotencyKey(request.idempotencyKey())
                .build());
        var membership = membershipRepository.save(Membership.builder()
                .member(member)
                .plan(plan)
                .payment(payment)
                .planName(plan.getName())
                .price(price)
                .startDate(startDate)
                .endDate(MembershipRules.endDate(startDate, plan.getDurationDays()))
                .build());
        return membershipMapper.toResponse(membership, today);
    }

    @Override
    @Transactional
    public MembershipResponse freezeMembershipById(Long membershipId) {
        var membership = getMembershipOrThrowById(membershipId);
        var today = LocalDate.now(clock);
        if (MembershipRules.effectiveStatus(membership, today) != MembershipStatus.ACTIVE)
            throw new BusinessException("Solo se puede congelar una membresía activa");
        membership.setFrozenSince(today);
        return membershipMapper.toResponse(membershipRepository.save(membership), today);
    }

    @Override
    @Transactional
    public MembershipResponse unfreezeMembershipById(Long membershipId) {
        var membership = getMembershipOrThrowById(membershipId);
        var today = LocalDate.now(clock);
        if (MembershipRules.effectiveStatus(membership, today) != MembershipStatus.FROZEN)
            throw new BusinessException("La membresía no está congelada");
        var days = (int) ChronoUnit.DAYS.between(membership.getFrozenSince(), today);
        if (days > 0) {
            // queued renewals move with the frozen membership so they never overlap it
            membershipRepository.findAllByMemberIdAndCancelledAtIsNullAndStartDateAfter(
                            membership.getMember().getId(), membership.getStartDate())
                    .forEach(next -> {
                        next.setStartDate(next.getStartDate().plusDays(days));
                        next.setEndDate(next.getEndDate().plusDays(days));
                    });
            membership.setEndDate(membership.getEndDate().plusDays(days));
            membership.setFrozenDays(membership.getFrozenDays() + days);
        }
        membership.setFrozenSince(null);
        return membershipMapper.toResponse(membershipRepository.save(membership), today);
    }

    @Override
    @Transactional
    public MembershipResponse cancelMembershipById(Long membershipId) {
        var membership = getMembershipOrThrowById(membershipId);
        var today = LocalDate.now(clock);
        switch (MembershipRules.effectiveStatus(membership, today)) {
            case CANCELLED -> throw new BusinessException("La membresía ya está cancelada");
            case EXPIRED -> throw new BusinessException("No se puede cancelar una membresía vencida");
            default -> membership.setCancelledAt(Instant.now(clock));
        }
        return membershipMapper.toResponse(membershipRepository.save(membership), today);
    }

    private Membership getMembershipOrThrowById(Long membershipId) {
        return membershipRepository.findWithPaymentById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada con id: " + membershipId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
