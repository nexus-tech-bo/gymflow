package com.example.gymflow.controller;

import com.example.gymflow.dto.cash.CashCloseRequest;
import com.example.gymflow.dto.cash.CashCurrentResponse;
import com.example.gymflow.dto.cash.CashOpenRequest;
import com.example.gymflow.dto.cash.CashSessionDetailResponse;
import com.example.gymflow.dto.cash.CashSessionResponse;
import com.example.gymflow.dto.common.PageResponse;
import com.example.gymflow.security.SecurityUtils;
import com.example.gymflow.service.CashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Cash register sessions.
 */
@RestController
@RequestMapping("/api/cash")
@RequiredArgsConstructor
@Tag(name = "Cash", description = "Cash Controller")
public class CashController {
    private final CashService cashService;

    @GetMapping("/current")
    @Operation(summary = "GET /api/cash/current — get the open cash session, if any")
    public ResponseEntity<CashCurrentResponse> findCurrentCashSession(Authentication auth) {
        return ResponseEntity.ok(cashService.findCurrentCashSession(SecurityUtils.extractRole(auth)));
    }

    @PostMapping("/open")
    @Operation(summary = "POST /api/cash/open — open the cash register")
    public ResponseEntity<CashSessionDetailResponse> openCashSession(@Valid @RequestBody CashOpenRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cashService.openCashSession(request, Long.parseLong(auth.getName()), SecurityUtils.extractRole(auth)));
    }

    @PostMapping("/close")
    @Operation(summary = "POST /api/cash/close — close the cash register (blind for RECEPTIONIST)")
    public ResponseEntity<CashSessionDetailResponse> closeCashSession(@Valid @RequestBody CashCloseRequest request, Authentication auth) {
        return ResponseEntity.ok(cashService.closeCashSession(request, Long.parseLong(auth.getName()), SecurityUtils.extractRole(auth)));
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "GET /api/cash/sessions — list cash sessions")
    public ResponseEntity<PageResponse<CashSessionResponse>> findAllCashSessions(
            @PageableDefault(size = 20, sort = "openedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(cashService.findAllCashSessions(pageable)));
    }

    @GetMapping("/sessions/{cashSessionId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "GET /api/cash/sessions/{cashSessionId} — get a cash session with its payments")
    public ResponseEntity<CashSessionDetailResponse> findCashSessionById(@PathVariable Long cashSessionId) {
        return ResponseEntity.ok(cashService.findCashSessionById(cashSessionId));
    }
}
