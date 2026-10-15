package com.example.gymflow.dto.checkin;

/**
 * Member identified at check-in.
 */
public record CheckInMemberResponse(
        Long id,
        String fullName
) {}
