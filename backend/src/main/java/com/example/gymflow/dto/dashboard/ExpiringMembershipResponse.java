package com.example.gymflow.dto.dashboard;

import java.time.LocalDate;

/**
 * A membership about to expire without a renewal.
 */
public record ExpiringMembershipResponse(
        Long memberId,
        String memberName,
        String planName,
        LocalDate endDate,
        int daysLeft
) {}
