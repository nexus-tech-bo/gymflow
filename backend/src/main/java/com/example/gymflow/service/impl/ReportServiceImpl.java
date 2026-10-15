package com.example.gymflow.service.impl;

import com.example.gymflow.dto.payment.PaymentTotals;
import com.example.gymflow.dto.report.IncomeDayResponse;
import com.example.gymflow.dto.report.IncomeReportResponse;
import com.example.gymflow.exception.BadRequestException;
import com.example.gymflow.repository.PaymentRepository;
import com.example.gymflow.repository.projection.PaymentLine;
import com.example.gymflow.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    @Override
    public IncomeReportResponse findIncomeReport(LocalDate from, LocalDate to) {
        if (to.isBefore(from))
            throw new BadRequestException("La fecha final no puede ser anterior a la inicial");
        if (to.isAfter(from.plusYears(1).minusDays(1)))
            throw new BadRequestException("El rango máximo es de 1 año");
        var zone = clock.getZone();
        var fromInstant = from.atStartOfDay(zone).toInstant();
        var toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();
        var totals = PaymentTotals.of(paymentRepository.sumByMethodBetween(fromInstant, toInstant));
        // ponytail: per-day grouping in memory (one row per payment in range); move to SQL if a year gets heavy
        var byDay = paymentRepository.findLinesBetween(fromInstant, toInstant).stream()
                .collect(Collectors.groupingBy(line -> LocalDate.ofInstant(line.paidAt(), zone), TreeMap::new, Collectors.toList()));
        var days = byDay.entrySet().stream()
                .map(e -> new IncomeDayResponse(e.getKey(), sum(e.getValue()), e.getValue().size()))
                .toList();
        return new IncomeReportResponse(from, to, totals, days);
    }

    private static BigDecimal sum(List<PaymentLine> lines) {
        return lines.stream().map(PaymentLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
