package com.example.gymflow.mapper;

import com.example.gymflow.dto.plan.PlanRequest;
import com.example.gymflow.dto.plan.PlanResponse;
import com.example.gymflow.entity.Plan;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * Maps plans.
 */
@Mapper(componentModel = "spring")
public interface PlanMapper {
    PlanResponse toResponse(Plan plan);

    List<PlanResponse> toResponseList(List<Plan> plans);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    Plan toEntity(PlanRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void toEntityUpdated(PlanRequest request, @MappingTarget Plan plan);
}
