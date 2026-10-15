package com.example.gymflow.service;

import com.example.gymflow.dto.cash.CashCloseRequest;
import com.example.gymflow.dto.cash.CashCurrentResponse;
import com.example.gymflow.dto.cash.CashOpenRequest;
import com.example.gymflow.dto.cash.CashSessionDetailResponse;
import com.example.gymflow.dto.cash.CashSessionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Cash register sessions. A RECEPTIONIST gets a blind view (no expected cash, totals or difference).
 */
public interface CashService {
    CashCurrentResponse findCurrentCashSession(String callerRole);

    /**
     * Opens the cash register.
     *
     * @throws com.example.gymflow.exception.BusinessException when a session is already open
     */
    CashSessionDetailResponse openCashSession(CashOpenRequest request, Long staffId, String callerRole);

    /**
     * Closes the open cash register with the counted cash.
     *
     * @throws com.example.gymflow.exception.BusinessException when no session is open
     */
    CashSessionDetailResponse closeCashSession(CashCloseRequest request, Long staffId, String callerRole);

    Page<CashSessionResponse> findAllCashSessions(Pageable pageable);

    CashSessionDetailResponse findCashSessionById(Long cashSessionId);
}
