package com.example.gymflow.controller;

import com.example.gymflow.dto.member.MemberCardResponse;
import com.example.gymflow.service.MemberCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public member card for the phone (no authentication).
 */
@RestController
@RequestMapping("/api/public/member-card")
@RequiredArgsConstructor
@Tag(name = "Public member card", description = "Public Member Card Controller")
public class PublicMemberCardController {
    private final MemberCardService memberCardService;

    @GetMapping("/{token}")
    @Operation(summary = "GET /api/public/member-card/{token} — public member card")
    public ResponseEntity<MemberCardResponse> findMemberCard(@PathVariable String token) {
        return ResponseEntity.ok(memberCardService.findMemberCard(token));
    }
}
