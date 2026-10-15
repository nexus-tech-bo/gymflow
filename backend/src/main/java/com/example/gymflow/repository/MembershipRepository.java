package com.example.gymflow.repository;

import com.example.gymflow.entity.Membership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Memberships of the current gym. Every query that feeds a {@code MembershipResponse} fetches the payment.
 */
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("SELECT m FROM Membership m LEFT JOIN FETCH m.payment " +
            "WHERE m.member.id = :memberId " +
            "ORDER BY m.startDate DESC, m.id DESC")
    List<Membership> findAllByMemberIdWithPayment(@Param("memberId") Long memberId);

    @Query("SELECT m FROM Membership m LEFT JOIN FETCH m.payment WHERE m.member.id IN :memberIds")
    List<Membership> findAllByMemberIdInWithPayment(@Param("memberIds") Collection<Long> memberIds);

    @EntityGraph(attributePaths = {"payment"})
    Optional<Membership> findWithPaymentById(Long id);

    @EntityGraph(attributePaths = {"payment"})
    Optional<Membership> findByPaymentIdempotencyKey(UUID idempotencyKey);

    Optional<Membership> findByPaymentId(Long paymentId);

    List<Membership> findAllByMemberIdAndCancelledAtIsNullAndStartDateAfter(Long memberId, LocalDate date);

    @Query("SELECT COUNT(DISTINCT m.member.id) FROM Membership m " +
            "WHERE m.member.active = true AND m.cancelledAt IS NULL AND m.frozenSince IS NULL " +
            "AND m.startDate <= :today AND m.endDate >= :today")
    long countActiveMembers(@Param("today") LocalDate today);

    @Query("SELECT COUNT(DISTINCT m.member.id) FROM Membership m " +
            "WHERE m.member.active = true AND m.cancelledAt IS NULL AND m.frozenSince IS NOT NULL")
    long countFrozenMembers();

    /**
     * Active memberships ending in {@code [today, until]} whose member has not renewed yet.
     */
    @Query("SELECT m FROM Membership m JOIN FETCH m.member mb " +
            "WHERE mb.active = true AND m.cancelledAt IS NULL AND m.frozenSince IS NULL " +
            "AND m.startDate <= :today AND m.endDate BETWEEN :today AND :until " +
            "AND NOT EXISTS (SELECT 1 FROM Membership n WHERE n.member = m.member " +
            "AND n.cancelledAt IS NULL AND n.startDate > m.endDate) " +
            "ORDER BY m.endDate ASC, m.id ASC")
    List<Membership> findExpiringBetween(@Param("today") LocalDate today, @Param("until") LocalDate until);
}
