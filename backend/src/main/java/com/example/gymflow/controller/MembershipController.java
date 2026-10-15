package com.example.gymflow.controller;

import com.example.gymflow.dto.membership.MembershipResponse;
import com.example.gymflow.dto.membership.MembershipSaleRequest;
import com.example.gymflow.security.SecurityUtils;
import com.example.gymflow.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Membership sales and lifecycle.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Memberships", description = "Memberships Controller")
public class MembershipController {
    private final MembershipService membershipService;

    @PostMapping("/members/{memberId}/memberships")
    @Operation(summary = "POST /api/members/{memberId}/memberships — sell or renew a membership (requires an open cash session)")
    public ResponseEntity<MembershipResponse> sellMembership(@PathVariable Long memberId, @Valid @RequestBody MembershipSaleRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(membershipService.sellMembership(memberId, request, Long.parseLong(auth.getName()), SecurityUtils.extractRole(auth)));
    }

    @PostMapping("/memberships/{membershipId}/freeze")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "POST /api/memberships/{membershipId}/freeze — freeze an active membership")
    public ResponseEntity<MembershipResponse> freezeMembershipById(@PathVariable Long membershipId) {
        return ResponseEntity.ok(membershipService.freezeMembershipById(membershipId));
    }

    @PostMapping("/memberships/{membershipId}/unfreeze")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "POST /api/memberships/{membershipId}/unfreeze — unfreeze a membership, extending it")
    public ResponseEntity<MembershipResponse> unfreezeMembershipById(@PathVariable Long membershipId) {
        return ResponseEntity.ok(membershipService.unfreezeMembershipById(membershipId));
    }

    @PostMapping("/memberships/{membershipId}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "POST /api/memberships/{membershipId}/cancel — cancel a membership")
    public ResponseEntity<MembershipResponse> cancelMembershipById(@PathVariable Long membershipId) {
        return ResponseEntity.ok(membershipService.cancelMembershipById(membershipId));
    }
}
