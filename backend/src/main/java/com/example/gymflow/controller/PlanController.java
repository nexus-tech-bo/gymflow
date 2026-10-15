package com.example.gymflow.controller;

import com.example.gymflow.dto.plan.PlanRequest;
import com.example.gymflow.dto.plan.PlanResponse;
import com.example.gymflow.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Membership plans.
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@Tag(name = "Plans", description = "Plans Controller")
public class PlanController {
    private final PlanService planService;

    @GetMapping
    @Operation(summary = "GET /api/plans — list plans, filter by ?includeInactive={bool}")
    public ResponseEntity<List<PlanResponse>> findAllPlans(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(planService.findAllPlans(includeInactive));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "POST /api/plans — create a plan")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody PlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.createPlan(request));
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "PUT /api/plans/{planId} — update a plan")
    public ResponseEntity<PlanResponse> updatePlanById(@PathVariable Long planId, @Valid @RequestBody PlanRequest request) {
        return ResponseEntity.ok(planService.updatePlanById(planId, request));
    }
}
