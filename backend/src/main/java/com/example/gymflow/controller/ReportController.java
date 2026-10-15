package com.example.gymflow.controller;

import com.example.gymflow.dto.report.IncomeReportResponse;
import com.example.gymflow.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reports (OWNER, ADMIN).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
@Tag(name = "Reports", description = "Reports Controller")
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/income")
    @Operation(summary = "GET /api/reports/income — income by method and day, filter by ?from={date}&to={date} (inclusive, max 1 year)")
    public ResponseEntity<IncomeReportResponse> findIncomeReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(reportService.findIncomeReport(from, to));
    }
}
