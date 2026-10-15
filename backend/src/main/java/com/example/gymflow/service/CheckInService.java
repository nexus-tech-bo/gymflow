package com.example.gymflow.service;

import com.example.gymflow.dto.checkin.CheckInEntryResponse;
import com.example.gymflow.dto.checkin.CheckInRequest;
import com.example.gymflow.dto.checkin.CheckInResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Gym entrance control.
 */
public interface CheckInService {
    /**
     * Decides whether the member may enter. Never throws for a denial: the reason is in the response.
     * A second allowed entry within 2 hours is reported as a duplicate and not recorded again.
     *
     * @param request the scanned or typed code
     * @return the decision
     */
    CheckInResponse checkIn(CheckInRequest request);

    /**
     * Check-in log of one day.
     *
     * @param date     the day, {@code null} for today
     * @param pageable the page request
     * @return the page of entries
     */
    Page<CheckInEntryResponse> findAllCheckIns(LocalDate date, Pageable pageable);
}
