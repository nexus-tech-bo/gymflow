package com.example.gymflow.service.impl;

import com.example.gymflow.dto.plan.PlanRequest;
import com.example.gymflow.dto.plan.PlanResponse;
import com.example.gymflow.entity.Plan;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.exception.ResourceNotFoundException;
import com.example.gymflow.mapper.PlanMapper;
import com.example.gymflow.repository.PlanRepository;
import com.example.gymflow.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanServiceImpl implements PlanService {
    private final PlanRepository planRepository;
    private final PlanMapper planMapper;

    @Override
    public List<PlanResponse> findAllPlans(boolean includeInactive) {
        var plans = includeInactive
                ? planRepository.findAllByOrderByIdAsc()
                : planRepository.findAllByActiveTrueOrderByIdAsc();
        return planMapper.toResponseList(plans);
    }

    @Override
    @Transactional
    public PlanResponse createPlan(PlanRequest request) {
        var name = request.name().trim();
        if (planRepository.existsByNameIgnoreCase(name))
            throw new BusinessException("Ya existe un plan con el nombre " + name);
        var plan = planMapper.toEntity(request);
        plan.setName(name);
        return planMapper.toResponse(planRepository.save(plan));
    }

    @Override
    @Transactional
    public PlanResponse updatePlanById(Long planId, PlanRequest request) {
        var plan = getPlanOrThrowById(planId);
        var name = request.name().trim();
        if (!plan.getName().equalsIgnoreCase(name) && planRepository.existsByNameIgnoreCase(name))
            throw new BusinessException("Ya existe un plan con el nombre " + name);
        planMapper.toEntityUpdated(request, plan);
        plan.setName(name);
        return planMapper.toResponse(planRepository.save(plan));
    }

    private Plan getPlanOrThrowById(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado con id: " + planId));
    }
}
