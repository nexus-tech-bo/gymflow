package com.example.gymflow.dto.report;

import com.example.gymflow.dto.payment.PaymentTotals;

import java.time.LocalDate;
import java.util.List;

/**
 * Income for an inclusive date range, with totals per method and per day (days without income omitted).
 */
public record IncomeReportResponse(
        LocalDate from,
        LocalDate to,
        PaymentTotals totals,
        List<IncomeDayResponse> days
) {}
