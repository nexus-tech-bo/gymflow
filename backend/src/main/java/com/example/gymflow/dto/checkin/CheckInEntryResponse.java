package com.example.gymflow.dto.checkin;

import com.example.gymflow.enums.CheckInDenialReason;
import com.example.gymflow.enums.CheckInMethod;
import com.example.gymflow.enums.CheckInResult;

import java.time.Instant;

/**
 * A row of the check-in log.
 */
public record CheckInEntryResponse(
        Long id,
        Instant checkedAt,
        Long memberId,
        String memberName,
        CheckInMethod method,
        CheckInResult result,
        CheckInDenialReason reason
) {}
