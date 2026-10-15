package com.example.gymflow.controller;

import com.example.gymflow.dto.auth.MeResponse;
import com.example.gymflow.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated user.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "Me", description = "Me Controller")
public class MeController {
    private final StaffService staffService;

    @GetMapping
    @Operation(summary = "GET /api/me — get the authenticated user")
    public ResponseEntity<MeResponse> findMe(Authentication auth) {
        return ResponseEntity.ok(staffService.findMe(Long.parseLong(auth.getName())));
    }
}
