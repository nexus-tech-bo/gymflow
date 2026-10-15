package com.example.gymflow.dto.cash;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Opens the cash session with the initial cash in the drawer.
 */
public record CashOpenRequest(
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("99999999.99")
        @Digits(integer = 8, fraction = 2)
        BigDecimal openingAmount
) {}
