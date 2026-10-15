package com.example.gymflow.service;

import com.example.gymflow.dto.dashboard.DashboardSummaryResponse;

/**
 * Dashboard figures.
 */
public interface DashboardService {
    DashboardSummaryResponse findSummary();
}
