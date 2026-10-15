package com.example.gymflow.dto.member;

import java.util.UUID;

/**
 * QR token of a member and the payload encoded in the QR image.
 */
public record MemberQrResponse(
        UUID qrToken,
        String payload
) {
    public static final String PAYLOAD_PREFIX = "GF1:";

    public static MemberQrResponse of(UUID qrToken) {
        return new MemberQrResponse(qrToken, PAYLOAD_PREFIX + qrToken);
    }
}
