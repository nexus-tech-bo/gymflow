package com.example.gymflow.dto.checkin;

import com.example.gymflow.enums.MembershipStatus;

import java.time.LocalDate;

/**
 * Membership that decided the check-in.
 */
public record CheckInMembershipResponse(
        String planName,
        LocalDate endDate,
        int daysLeft,
        MembershipStatus status
) {}
