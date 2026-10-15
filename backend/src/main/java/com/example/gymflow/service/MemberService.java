package com.example.gymflow.service;

import com.example.gymflow.dto.member.MemberActiveRequest;
import com.example.gymflow.dto.member.MemberDetailResponse;
import com.example.gymflow.dto.member.MemberQrResponse;
import com.example.gymflow.dto.member.MemberRequest;
import com.example.gymflow.dto.member.MemberSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Members of the current gym and their QR codes.
 */
public interface MemberService {
    /**
     * Searches members by name or DNI.
     *
     * @param q        the search text, {@code null} or blank for all
     * @param pageable the page request
     * @return the page of members with their current membership
     */
    Page<MemberSummaryResponse> findAllMembers(String q, Pageable pageable);

    MemberDetailResponse findMemberById(Long memberId);

    MemberDetailResponse createMember(MemberRequest request);

    MemberDetailResponse updateMemberById(Long memberId, MemberRequest request);

    MemberDetailResponse updateMemberActiveById(Long memberId, MemberActiveRequest request);

    MemberQrResponse findMemberQrById(Long memberId);

    /**
     * Replaces the QR token; the previous card and link stop working.
     *
     * @param memberId the member id
     * @return the new QR
     */
    MemberQrResponse rotateMemberQrById(Long memberId);
}
