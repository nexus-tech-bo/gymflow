package com.example.gymflow.service.impl;

import com.example.gymflow.config.tenant.TenantContext;
import com.example.gymflow.dto.member.MemberCardResponse;
import com.example.gymflow.dto.member.MemberQrResponse;
import com.example.gymflow.entity.Member;
import com.example.gymflow.entity.Membership;
import com.example.gymflow.exception.ResourceNotFoundException;
import com.example.gymflow.repository.GymRepository;
import com.example.gymflow.repository.MemberCardTokenRepository;
import com.example.gymflow.repository.MemberRepository;
import com.example.gymflow.repository.MembershipRepository;
import com.example.gymflow.service.MemberCardService;
import com.example.gymflow.service.support.MembershipRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Resolves the tenant from the public token registry, then reads the member in that tenant. No class-level
 * transaction: the tenant must be set before the tenant transaction starts.
 */
@Service
@RequiredArgsConstructor
public class MemberCardServiceImpl implements MemberCardService {
    private static final String NOT_FOUND = "Carnet no encontrado";

    private final MemberCardTokenRepository memberCardTokenRepository;
    private final GymRepository gymRepository;
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Override
    public MemberCardResponse findMemberCard(String token) {
        var qrToken = parseToken(token);
        var card = memberCardTokenRepository.findById(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        var gym = gymRepository.findById(card.getGymId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        var tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        try {
            TenantContext.setCurrentTenant(TenantContext.schemaOf(gym.getId()));
            return tx.execute(_ -> {
                var member = memberRepository.findByQrToken(qrToken)
                        .filter(Member::isActive)
                        .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
                var today = LocalDate.now(clock);
                var current = MembershipRules.pickCurrent(membershipRepository.findAllByMemberIdWithPayment(member.getId()), today);
                return new MemberCardResponse(
                        gym.getName(),
                        member.getFullName(),
                        MemberQrResponse.PAYLOAD_PREFIX + qrToken,
                        current.map(Membership::getPlanName).orElse(null),
                        current.map(Membership::getEndDate).orElse(null),
                        current.map(m -> MembershipRules.effectiveStatus(m, today)).orElse(null));
            });
        } finally {
            TenantContext.clear();
        }
    }

    private static UUID parseToken(String token) {
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException _) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
    }
}
