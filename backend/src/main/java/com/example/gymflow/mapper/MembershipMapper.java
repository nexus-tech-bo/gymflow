package com.example.gymflow.mapper;

import com.example.gymflow.dto.checkin.CheckInMembershipResponse;
import com.example.gymflow.dto.dashboard.ExpiringMembershipResponse;
import com.example.gymflow.dto.membership.MembershipPaymentResponse;
import com.example.gymflow.dto.membership.MembershipResponse;
import com.example.gymflow.entity.Membership;
import com.example.gymflow.entity.Payment;
import com.example.gymflow.service.support.MembershipRules;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.util.List;

/**
 * Maps memberships; status and days left are evaluated as of the {@code today} context.
 */
@Mapper(componentModel = "spring", imports = MembershipRules.class)
public interface MembershipMapper {
    @Mapping(target = "status", expression = "java(MembershipRules.effectiveStatus(membership, today))")
    @Mapping(target = "daysLeft", expression = "java(MembershipRules.daysLeft(membership, today))")
    MembershipResponse toResponse(Membership membership, @Context LocalDate today);

    List<MembershipResponse> toResponseList(List<Membership> memberships, @Context LocalDate today);

    MembershipPaymentResponse toPaymentResponse(Payment payment);

    @Mapping(target = "status", expression = "java(MembershipRules.effectiveStatus(membership, today))")
    @Mapping(target = "daysLeft", expression = "java(MembershipRules.daysLeft(membership, today))")
    CheckInMembershipResponse toCheckInResponse(Membership membership, @Context LocalDate today);

    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "memberName", source = "member.fullName")
    @Mapping(target = "daysLeft", expression = "java(MembershipRules.daysLeft(membership, today))")
    ExpiringMembershipResponse toExpiringResponse(Membership membership, @Context LocalDate today);
}
