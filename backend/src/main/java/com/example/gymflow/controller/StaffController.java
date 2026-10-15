package com.example.gymflow.controller;

import com.example.gymflow.dto.staff.StaffRequest;
import com.example.gymflow.dto.staff.StaffResponse;
import com.example.gymflow.dto.staff.StaffUpdateRequest;
import com.example.gymflow.security.SecurityUtils;
import com.example.gymflow.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Staff management (OWNER, ADMIN).
 */
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
@Tag(name = "Staff", description = "Staff Controller")
public class StaffController {
    private final StaffService staffService;

    @GetMapping
    @Operation(summary = "GET /api/staff — list all staff users")
    public ResponseEntity<List<StaffResponse>> findAllStaff() {
        return ResponseEntity.ok(staffService.findAllStaff());
    }

    @PostMapping
    @Operation(summary = "POST /api/staff — create a staff user (ADMIN may only create RECEPTIONIST)")
    public ResponseEntity<StaffResponse> createStaff(@Valid @RequestBody StaffRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffService.createStaff(request, Long.parseLong(auth.getName()), SecurityUtils.extractRole(auth)));
    }

    @PatchMapping("/{staffId}")
    @Operation(summary = "PATCH /api/staff/{staffId} — partially update a staff user")
    public ResponseEntity<StaffResponse> updateStaffById(@PathVariable Long staffId, @Valid @RequestBody StaffUpdateRequest request, Authentication auth) {
        return ResponseEntity.ok(staffService.updateStaffById(staffId, request, Long.parseLong(auth.getName()), SecurityUtils.extractRole(auth)));
    }
}
