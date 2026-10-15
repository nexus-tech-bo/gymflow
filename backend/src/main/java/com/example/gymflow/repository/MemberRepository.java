package com.example.gymflow.repository;

import com.example.gymflow.entity.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Members of the current gym.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByDni(String dni);

    /**
     * Loads the member with {@code FOR UPDATE}, serializing sales of the same member (no overlapping renewals,
     * concurrent retries with the same idempotency key see the first sale).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Member> findLockedById(Long id);

    Optional<Member> findByDni(String dni);

    Optional<Member> findByQrToken(UUID qrToken);

    /**
     * Searches by name (contains, case-insensitive) or DNI (prefix). An empty {@code q} matches every member.
     */
    @Query("SELECT m FROM Member m " +
            "WHERE LOWER(m.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "m.dni LIKE CONCAT(:q, '%')")
    Page<Member> search(@Param("q") String q, Pageable pageable);
}
