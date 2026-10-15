package com.example.gymflow.repository;

import com.example.gymflow.entity.MemberCardToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * QR token to gym registry (public schema).
 */
public interface MemberCardTokenRepository extends JpaRepository<MemberCardToken, UUID> {
}
