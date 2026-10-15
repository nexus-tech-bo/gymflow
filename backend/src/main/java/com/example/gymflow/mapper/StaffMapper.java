package com.example.gymflow.mapper;

import com.example.gymflow.dto.auth.MeResponse;
import com.example.gymflow.dto.staff.StaffResponse;
import com.example.gymflow.entity.Staff;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Maps staff users, flattening their account (and gym for {@link MeResponse}).
 */
@Mapper(componentModel = "spring")
public interface StaffMapper {
    @Mapping(target = "fullName", source = "account.fullName")
    @Mapping(target = "email", source = "account.email")
    StaffResponse toResponse(Staff staff);

    List<StaffResponse> toResponseList(List<Staff> staff);

    @Mapping(target = "fullName", source = "account.fullName")
    @Mapping(target = "email", source = "account.email")
    @Mapping(target = "gymId", source = "account.gym.id")
    @Mapping(target = "gymName", source = "account.gym.name")
    MeResponse toMeResponse(Staff staff);
}
