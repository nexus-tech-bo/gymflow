package com.example.gymflow.dto.checkin;

import com.example.gymflow.enums.CheckInDenialReason;
import com.example.gymflow.enums.CheckInResult;

import java.time.Instant;

/**
 * Check-in decision. When {@code duplicate} is true, {@code checkedAt} is the first entry of the last 2 hours.
 */
public record CheckInResponse(
        CheckInResult result,
        boolean duplicate,
        CheckInDenialReason reason,
        String message,
        CheckInMemberResponse member,
        CheckInMembershipResponse membership,
        Instant checkedAt
) {}
