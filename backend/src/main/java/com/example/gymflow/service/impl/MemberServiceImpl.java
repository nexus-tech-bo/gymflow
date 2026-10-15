package com.example.gymflow.service.impl;

import com.example.gymflow.config.tenant.TenantContext;
import com.example.gymflow.dto.member.MemberActiveRequest;
import com.example.gymflow.dto.member.MemberDetailResponse;
import com.example.gymflow.dto.member.MemberQrResponse;
import com.example.gymflow.dto.member.MemberRequest;
import com.example.gymflow.dto.member.MemberSummaryResponse;
import com.example.gymflow.dto.membership.MembershipResponse;
import com.example.gymflow.entity.Member;
import com.example.gymflow.entity.MemberCardToken;
import com.example.gymflow.entity.Membership;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.exception.ResourceNotFoundException;
import com.example.gymflow.mapper.MemberMapper;
import com.example.gymflow.mapper.MembershipMapper;
import com.example.gymflow.repository.MemberCardTokenRepository;
import com.example.gymflow.repository.MemberRepository;
import com.example.gymflow.repository.MembershipRepository;
import com.example.gymflow.service.MemberService;
import com.example.gymflow.service.support.MembershipRules;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final MemberCardTokenRepository memberCardTokenRepository;
    private final MemberMapper memberMapper;
    private final MembershipMapper membershipMapper;
    private final Clock clock;

    @Override
    public Page<MemberSummaryResponse> findAllMembers(String q, Pageable pageable) {
        var members = memberRepository.search(q == null ? "" : q.trim(), pageable);
        var ids = members.map(Member::getId).getContent();
        Map<Long, List<Membership>> byMember = ids.isEmpty()
                ? Map.of()
                : membershipRepository.findAllByMemberIdInWithPayment(ids).stream()
                        .collect(Collectors.groupingBy(m -> m.getMember().getId()));
        var today = LocalDate.now(clock);
        return members.map(member -> memberMapper.toSummaryResponse(member,
                currentMembership(byMember.getOrDefault(member.getId(), List.of()), today)));
    }

    @Override
    public MemberDetailResponse findMemberById(Long memberId) {
        return toDetailResponse(getMemberOrThrowById(memberId));
    }

    @Override
    @Transactional
    public MemberDetailResponse createMember(MemberRequest request) {
        var dni = normalizeDni(request.dni());
        if (memberRepository.existsByDni(dni))
            throw new BusinessException("Ya existe un socio con el DNI " + dni);
        var member = memberMapper.toEntity(request);
        member.setDni(dni);
        member.setQrToken(UUID.randomUUID());
        member = memberRepository.save(member);
        memberCardTokenRepository.save(new MemberCardToken(member.getQrToken(), TenantContext.currentGymId()));
        return toDetailResponse(member);
    }

    @Override
    @Transactional
    public MemberDetailResponse updateMemberById(Long memberId, MemberRequest request) {
        var member = getMemberOrThrowById(memberId);
        var dni = normalizeDni(request.dni());
        if (!member.getDni().equals(dni) && memberRepository.existsByDni(dni))
            throw new BusinessException("Ya existe un socio con el DNI " + dni);
        memberMapper.toEntityUpdated(request, member);
        member.setDni(dni);
        return toDetailResponse(memberRepository.save(member));
    }

    @Override
    @Transactional
    public MemberDetailResponse updateMemberActiveById(Long memberId, MemberActiveRequest request) {
        var member = getMemberOrThrowById(memberId);
        member.setActive(request.active());
        return toDetailResponse(memberRepository.save(member));
    }

    @Override
    public MemberQrResponse findMemberQrById(Long memberId) {
        return MemberQrResponse.of(getMemberOrThrowById(memberId).getQrToken());
    }

    @Override
    @Transactional
    public MemberQrResponse rotateMemberQrById(Long memberId) {
        var member = getMemberOrThrowById(memberId);
        memberCardTokenRepository.deleteById(member.getQrToken());
        member.setQrToken(UUID.randomUUID());
        memberRepository.save(member);
        memberCardTokenRepository.save(new MemberCardToken(member.getQrToken(), TenantContext.currentGymId()));
        return MemberQrResponse.of(member.getQrToken());
    }

    private MemberDetailResponse toDetailResponse(Member member) {
        var memberships = membershipRepository.findAllByMemberIdWithPayment(member.getId());
        var today = LocalDate.now(clock);
        return memberMapper.toDetailResponse(member, currentMembership(memberships, today),
                membershipMapper.toResponseList(memberships, today));
    }

    private MembershipResponse currentMembership(List<Membership> memberships, LocalDate today) {
        return MembershipRules.pickCurrent(memberships, today)
                .map(m -> membershipMapper.toResponse(m, today))
                .orElse(null);
    }

    private Member getMemberOrThrowById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Socio no encontrado con id: " + memberId));
    }

    private static String normalizeDni(String dni) {
        return dni.trim().toUpperCase(Locale.ROOT);
    }
}
