package com.example.gymflow.dto.member;

import com.example.gymflow.dto.membership.MembershipResponse;

/**
 * Member row of the paginated search.
 */
public record MemberSummaryResponse(
        Long id,
        String fullName,
        String dni,
        String phone,
        boolean active,
        MembershipResponse currentMembership
) {}
