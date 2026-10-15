package com.example.gymflow.mapper;

import com.example.gymflow.dto.member.MemberDetailResponse;
import com.example.gymflow.dto.member.MemberRequest;
import com.example.gymflow.dto.member.MemberSummaryResponse;
import com.example.gymflow.dto.membership.MembershipResponse;
import com.example.gymflow.entity.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Maps members; membership parts arrive already mapped.
 */
@Mapper(componentModel = "spring")
public interface MemberMapper {
    @Mapping(target = "id", source = "member.id")
    MemberDetailResponse toDetailResponse(Member member, MembershipResponse currentMembership, List<MembershipResponse> memberships);

    @Mapping(target = "id", source = "member.id")
    MemberSummaryResponse toSummaryResponse(Member member, MembershipResponse currentMembership);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "qrToken", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Member toEntity(MemberRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "qrToken", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void toEntityUpdated(MemberRequest request, @MappingTarget Member member);
}
