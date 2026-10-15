package com.example.gymflow.repository;

import com.example.gymflow.entity.CheckIn;
import com.example.gymflow.enums.CheckInResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Check-ins of the current gym.
 */
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    Optional<CheckIn> findFirstByMemberIdAndResultAndCheckedAtAfterOrderByCheckedAtAsc(
            Long memberId, CheckInResult result, Instant since);

    @Query(value = "SELECT c FROM CheckIn c JOIN FETCH c.member " +
            "WHERE c.checkedAt >= :from AND c.checkedAt < :to",
            countQuery = "SELECT COUNT(c) FROM CheckIn c WHERE c.checkedAt >= :from AND c.checkedAt < :to")
    Page<CheckIn> findAllBetween(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.result = :result AND c.checkedAt >= :from AND c.checkedAt < :to")
    long countByResultBetween(@Param("result") CheckInResult result, @Param("from") Instant from, @Param("to") Instant to);
}
