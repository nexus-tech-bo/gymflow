package com.example.gymflow.dto.member;

import com.example.gymflow.enums.MembershipStatus;

import java.time.LocalDate;

/**
 * Public member card; membership fields are {@code null} when the member has no membership.
 */
public record MemberCardResponse(
        String gymName,
        String memberName,
        String payload,
        String planName,
        LocalDate endDate,
        MembershipStatus status
) {}
