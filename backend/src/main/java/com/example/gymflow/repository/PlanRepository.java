package com.example.gymflow.repository;

import com.example.gymflow.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Plans of the current gym.
 */
public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findAllByOrderByIdAsc();

    List<Plan> findAllByActiveTrueOrderByIdAsc();

    boolean existsByNameIgnoreCase(String name);
}
