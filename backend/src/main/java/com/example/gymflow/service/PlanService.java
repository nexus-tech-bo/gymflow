package com.example.gymflow.service;

import com.example.gymflow.dto.plan.PlanRequest;
import com.example.gymflow.dto.plan.PlanResponse;

import java.util.List;

/**
 * Membership plans of the current gym.
 */
public interface PlanService {
    List<PlanResponse> findAllPlans(boolean includeInactive);

    PlanResponse createPlan(PlanRequest request);

    PlanResponse updatePlanById(Long planId, PlanRequest request);
}
