package com.example.gymflow.repository;

import com.example.gymflow.entity.CashSession;
import com.example.gymflow.enums.CashSessionStatus;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

/**
 * Cash sessions of the current gym.
 * <p>
 * Locking: sales and voids take a shared lock ({@code FOR SHARE}) on the open session; close takes an exclusive
 * one ({@code FOR UPDATE}). Close therefore waits for in-flight payments, and payments that arrive while it runs
 * re-check {@code status = OPEN} after it commits and find nothing (409).
 */
public interface CashSessionRepository extends JpaRepository<CashSession, Long> {
    @EntityGraph(attributePaths = {"openedBy.account", "closedBy.account"})
    Optional<CashSession> findFirstByStatus(CashSessionStatus status);

    /** Shared lock, used when adding a payment to the open session. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<CashSession> findSharedLockedByStatus(CashSessionStatus status);

    /** Shared lock on a given session, present only while it has the given status (used to void a payment). */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<CashSession> findSharedLockedByIdAndStatus(Long id, CashSessionStatus status);

    /** Exclusive lock, used to close the open session. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CashSession> findLockedByStatus(CashSessionStatus status);

    boolean existsByStatus(CashSessionStatus status);

    @NullMarked
    @EntityGraph(attributePaths = {"openedBy.account", "closedBy.account"})
    Page<CashSession> findAll(Pageable pageable);

    @NullMarked
    @EntityGraph(attributePaths = {"openedBy.account", "closedBy.account"})
    Optional<CashSession> findById(Long id);
}
