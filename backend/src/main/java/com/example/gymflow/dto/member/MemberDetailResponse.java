package com.example.gymflow.dto.member;

import com.example.gymflow.dto.membership.MembershipResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A member with its current membership and full history (most recent first).
 */
public record MemberDetailResponse(
        Long id,
        String fullName,
        String dni,
        String phone,
        String email,
        LocalDate birthDate,
        String notes,
        boolean active,
        Instant createdAt,
        MembershipResponse currentMembership,
        List<MembershipResponse> memberships
) {}
