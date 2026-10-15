package com.example.gymflow.dto.membership;

import com.example.gymflow.enums.MembershipStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A membership with its effective status as of today.
 */
public record MembershipResponse(
        Long id,
        String planName,
        BigDecimal price,
        LocalDate startDate,
        LocalDate endDate,
        MembershipStatus status,
        LocalDate frozenSince,
        int frozenDays,
        int daysLeft,
        MembershipPaymentResponse payment
) {}
