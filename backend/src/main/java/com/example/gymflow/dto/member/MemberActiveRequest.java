package com.example.gymflow.dto.member;

import jakarta.validation.constraints.NotNull;

/**
 * Activates or deactivates a member.
 */
public record MemberActiveRequest(
        @NotNull
        Boolean active
) {}
