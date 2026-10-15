package com.example.gymflow.repository;

import com.example.gymflow.entity.Payment;
import com.example.gymflow.repository.projection.MethodTotal;
import com.example.gymflow.repository.projection.PaymentLine;
import com.example.gymflow.repository.projection.SessionMethodTotal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Payments of the current gym.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("SELECT p FROM Payment p " +
            "JOIN FETCH p.member " +
            "JOIN FETCH p.receivedBy rb JOIN FETCH rb.account " +
            "WHERE p.cashSession.id = :cashSessionId " +
            "ORDER BY p.paidAt ASC, p.id ASC")
    List<Payment> findAllByCashSessionId(@Param("cashSessionId") Long cashSessionId);

    @EntityGraph(attributePaths = {"member", "receivedBy.account", "cashSession"})
    Optional<Payment> findWithDetailsById(Long id);

    /**
     * Non-voided totals per session and method.
     */
    @Query("SELECT new com.example.gymflow.repository.projection.SessionMethodTotal(" +
            "p.cashSession.id, p.method, SUM(p.amount), COUNT(p)) " +
            "FROM Payment p WHERE p.cashSession.id IN :sessionIds AND p.voided = false " +
            "GROUP BY p.cashSession.id, p.method")
    List<SessionMethodTotal> sumBySessionAndMethod(@Param("sessionIds") Collection<Long> sessionIds);

    /**
     * Non-voided totals per method for payments in {@code [from, to)}.
     */
    @Query("SELECT new com.example.gymflow.repository.projection.MethodTotal(p.method, SUM(p.amount), COUNT(p)) " +
            "FROM Payment p WHERE p.voided = false AND p.paidAt >= :from AND p.paidAt < :to " +
            "GROUP BY p.method")
    List<MethodTotal> sumByMethodBetween(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Non-voided payments in {@code [from, to)} reduced to instant and amount, for daily grouping.
     */
    @Query("SELECT new com.example.gymflow.repository.projection.PaymentLine(p.paidAt, p.amount) " +
            "FROM Payment p WHERE p.voided = false AND p.paidAt >= :from AND p.paidAt < :to")
    List<PaymentLine> findLinesBetween(@Param("from") Instant from, @Param("to") Instant to);
}
