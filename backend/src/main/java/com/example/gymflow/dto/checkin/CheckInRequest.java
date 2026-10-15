package com.example.gymflow.dto.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * QR payload ({@code GF1:<uuid>}), bare QR uuid, or DNI.
 */
public record CheckInRequest(
        @NotBlank
        @Size(max = 100)
        String code
) {}
