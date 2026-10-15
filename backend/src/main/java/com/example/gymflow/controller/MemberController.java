package com.example.gymflow.controller;

import com.example.gymflow.dto.common.PageResponse;
import com.example.gymflow.dto.member.MemberActiveRequest;
import com.example.gymflow.dto.member.MemberDetailResponse;
import com.example.gymflow.dto.member.MemberQrResponse;
import com.example.gymflow.dto.member.MemberRequest;
import com.example.gymflow.dto.member.MemberSummaryResponse;
import com.example.gymflow.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Members (socios) and their QR codes.
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@Tag(name = "Members", description = "Members Controller")
public class MemberController {
    private final MemberService memberService;

    @GetMapping
    @Operation(summary = "GET /api/members — search members, filter by ?q={name or dni}")
    public ResponseEntity<PageResponse<MemberSummaryResponse>> findAllMembers(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(memberService.findAllMembers(q, pageable)));
    }

    @PostMapping
    @Operation(summary = "POST /api/members — create a member")
    public ResponseEntity<MemberDetailResponse> createMember(@Valid @RequestBody MemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.createMember(request));
    }

    @GetMapping("/{memberId}")
    @Operation(summary = "GET /api/members/{memberId} — get a member with its memberships")
    public ResponseEntity<MemberDetailResponse> findMemberById(@PathVariable Long memberId) {
        return ResponseEntity.ok(memberService.findMemberById(memberId));
    }

    @PutMapping("/{memberId}")
    @Operation(summary = "PUT /api/members/{memberId} — update a member")
    public ResponseEntity<MemberDetailResponse> updateMemberById(@PathVariable Long memberId, @Valid @RequestBody MemberRequest request) {
        return ResponseEntity.ok(memberService.updateMemberById(memberId, request));
    }

    @PatchMapping("/{memberId}/active")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "PATCH /api/members/{memberId}/active — activate or deactivate a member")
    public ResponseEntity<MemberDetailResponse> updateMemberActiveById(@PathVariable Long memberId, @Valid @RequestBody MemberActiveRequest request) {
        return ResponseEntity.ok(memberService.updateMemberActiveById(memberId, request));
    }

    @GetMapping("/{memberId}/qr")
    @Operation(summary = "GET /api/members/{memberId}/qr — get the member QR")
    public ResponseEntity<MemberQrResponse> findMemberQrById(@PathVariable Long memberId) {
        return ResponseEntity.ok(memberService.findMemberQrById(memberId));
    }

    @PostMapping("/{memberId}/qr/rotate")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "POST /api/members/{memberId}/qr/rotate — issue a new QR, invalidating the previous one")
    public ResponseEntity<MemberQrResponse> rotateMemberQrById(@PathVariable Long memberId) {
        return ResponseEntity.ok(memberService.rotateMemberQrById(memberId));
    }
}
