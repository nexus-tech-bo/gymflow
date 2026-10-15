package com.example.gymflow.repository;

import com.example.gymflow.entity.Gym;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Gyms registry (public schema).
 */
public interface GymRepository extends JpaRepository<Gym, Long> {
}
