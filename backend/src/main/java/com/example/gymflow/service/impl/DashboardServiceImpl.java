package com.example.gymflow.service.impl;

import com.example.gymflow.dto.dashboard.DashboardSummaryResponse;
import com.example.gymflow.enums.CheckInResult;
import com.example.gymflow.mapper.MembershipMapper;
import com.example.gymflow.repository.CheckInRepository;
import com.example.gymflow.repository.MembershipRepository;
import com.example.gymflow.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private static final int EXPIRING_WINDOW_DAYS = 7;

    private final MembershipRepository membershipRepository;
    private final CheckInRepository checkInRepository;
    private final MembershipMapper membershipMapper;
    private final Clock clock;

    @Override
    public DashboardSummaryResponse findSummary() {
        var today = LocalDate.now(clock);
        var zone = clock.getZone();
        var checkInsToday = checkInRepository.countByResultBetween(CheckInResult.ALLOWED,
                today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant());
        var expiring = membershipRepository.findExpiringBetween(today, today.plusDays(EXPIRING_WINDOW_DAYS)).stream()
                .map(m -> membershipMapper.toExpiringResponse(m, today))
                .toList();
        return new DashboardSummaryResponse(
                membershipRepository.countActiveMembers(today),
                membershipRepository.countFrozenMembers(),
                checkInsToday,
                expiring);
    }
}
