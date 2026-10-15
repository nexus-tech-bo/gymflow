package com.example.gymflow.dto.dashboard;

import java.util.List;

/**
 * Dashboard counters and memberships expiring in the next days.
 */
public record DashboardSummaryResponse(
        long activeMembers,
        long frozenMembers,
        long checkInsToday,
        List<ExpiringMembershipResponse> expiringSoon
) {}
