package com.example.gymflow.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Income of one day.
 */
public record IncomeDayResponse(
        LocalDate date,
        BigDecimal total,
        long count
) {}
