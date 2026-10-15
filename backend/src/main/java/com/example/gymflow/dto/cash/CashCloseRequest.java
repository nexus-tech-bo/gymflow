package com.example.gymflow.dto.cash;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Closes the open cash session with the counted cash.
 */
public record CashCloseRequest(
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("99999999.99")
        @Digits(integer = 8, fraction = 2)
        BigDecimal countedCash,
        @Size(max = 500)
        String notes
) {}
