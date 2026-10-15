package com.example.gymflow.repository;

import com.example.gymflow.entity.Staff;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Staff of the current gym.
 */
public interface StaffRepository extends JpaRepository<Staff, Long> {
    @EntityGraph(attributePaths = {"account"})
    List<Staff> findAllByOrderByIdAsc();

    @NullMarked
    @EntityGraph(attributePaths = {"account"})
    Optional<Staff> findById(Long id);

    @EntityGraph(attributePaths = {"account", "account.gym"})
    Optional<Staff> findWithGymById(Long id);

    @EntityGraph(attributePaths = {"account", "account.gym"})
    Optional<Staff> findByAccountId(Long accountId);
}
