package com.example.gymflow.controller;

import com.example.gymflow.dto.checkin.CheckInEntryResponse;
import com.example.gymflow.dto.checkin.CheckInRequest;
import com.example.gymflow.dto.checkin.CheckInResponse;
import com.example.gymflow.dto.common.PageResponse;
import com.example.gymflow.service.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Entrance control.
 */
@RestController
@RequestMapping("/api/check-ins")
@RequiredArgsConstructor
@Tag(name = "Check-ins", description = "Check-ins Controller")
public class CheckInController {
    private final CheckInService checkInService;

    @PostMapping
    @Operation(summary = "POST /api/check-ins — check a member in by QR or DNI (200 even when denied)")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.ok(checkInService.checkIn(request));
    }

    @GetMapping
    @Operation(summary = "GET /api/check-ins — list check-ins of a day, filter by ?date={date} (today by default)")
    public ResponseEntity<PageResponse<CheckInEntryResponse>> findAllCheckIns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PageableDefault(size = 50, sort = "checkedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(checkInService.findAllCheckIns(date, pageable)));
    }
}
