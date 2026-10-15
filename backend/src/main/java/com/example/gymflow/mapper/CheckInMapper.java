package com.example.gymflow.mapper;

import com.example.gymflow.dto.checkin.CheckInEntryResponse;
import com.example.gymflow.entity.CheckIn;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps check-in log entries.
 */
@Mapper(componentModel = "spring")
public interface CheckInMapper {
    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "memberName", source = "member.fullName")
    CheckInEntryResponse toEntryResponse(CheckIn checkIn);
}
