package com.example.gymflow.service;

import com.example.gymflow.dto.member.MemberCardResponse;

/**
 * Public (unauthenticated) member card.
 */
public interface MemberCardService {
    /**
     * Resolves the gym from the QR token and returns the member card.
     *
     * @param token the member QR token
     * @return the card
     * @throws com.example.gymflow.exception.ResourceNotFoundException when the token is unknown or stale, or the member is inactive
     */
    MemberCardResponse findMemberCard(String token);
}
