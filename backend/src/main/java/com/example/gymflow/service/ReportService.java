package com.example.gymflow.service;

import com.example.gymflow.dto.report.IncomeReportResponse;

import java.time.LocalDate;

/**
 * Income reports.
 */
public interface ReportService {
    /**
     * Non-voided income for an inclusive range of at most one year.
     *
     * @throws com.example.gymflow.exception.BadRequestException when the range is reversed or longer than a year
     */
    IncomeReportResponse findIncomeReport(LocalDate from, LocalDate to);
}
