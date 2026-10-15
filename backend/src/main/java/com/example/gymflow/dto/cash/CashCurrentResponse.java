package com.example.gymflow.dto.cash;

/**
 * The open cash session, or {@code null} when the cash register is closed.
 */
public record CashCurrentResponse(
        CashSessionDetailResponse current
) {}
