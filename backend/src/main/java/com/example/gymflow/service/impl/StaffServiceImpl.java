package com.example.gymflow.service.impl;

import com.example.gymflow.dto.auth.MeResponse;
import com.example.gymflow.dto.staff.StaffRequest;
import com.example.gymflow.dto.staff.StaffResponse;
import com.example.gymflow.dto.staff.StaffUpdateRequest;
import com.example.gymflow.entity.Account;
import com.example.gymflow.entity.Staff;
import com.example.gymflow.enums.Role;
import com.example.gymflow.exception.BadRequestException;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.exception.ForbiddenException;
import com.example.gymflow.exception.ResourceNotFoundException;
import com.example.gymflow.mapper.StaffMapper;
import com.example.gymflow.repository.AccountRepository;
import com.example.gymflow.repository.StaffRepository;
import com.example.gymflow.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffServiceImpl implements StaffService {
    private final StaffRepository staffRepository;
    private final AccountRepository accountRepository;
    private final StaffMapper staffMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public MeResponse findMe(Long staffId) {
        return staffRepository.findWithGymById(staffId)
                .map(staffMapper::toMeResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + staffId));
    }

    @Override
    public List<StaffResponse> findAllStaff() {
        return staffMapper.toResponseList(staffRepository.findAllByOrderByIdAsc());
    }

    @Override
    @Transactional
    public StaffResponse createStaff(StaffRequest request, Long callerId, String callerRole) {
        if (request.role() == Role.OWNER)
            throw new BadRequestException("No se puede crear otro OWNER");
        if (Role.ADMIN.name().equals(callerRole) && request.role() != Role.RECEPTIONIST)
            throw new ForbiddenException("ADMIN solo puede crear RECEPTIONIST");
        var email = request.email().trim().toLowerCase(Locale.ROOT);
        if (accountRepository.existsByEmail(email))
            throw new BusinessException("El email ya está registrado");
        var caller = getStaffOrThrowById(callerId);
        var staff = Staff.builder()
                .account(Account.builder()
                        .gym(caller.getAccount().getGym())
                        .fullName(request.fullName().trim())
                        .email(email)
                        .password(passwordEncoder.encode(request.password()))
                        .build())
                .role(request.role())
                .build();
        return staffMapper.toResponse(staffRepository.save(staff));
    }

    @Override
    @Transactional
    public StaffResponse updateStaffById(Long staffId, StaffUpdateRequest request, Long callerId, String callerRole) {
        var staff = getStaffOrThrowById(staffId);
        var changesRole = request.role() != null && request.role() != staff.getRole();
        var deactivates = Boolean.FALSE.equals(request.active());
        if (changesRole && request.role() == Role.OWNER)
            throw new BadRequestException("No se puede asignar el rol OWNER");
        if (Role.ADMIN.name().equals(callerRole) && (staff.getRole() != Role.RECEPTIONIST || changesRole))
            throw new ForbiddenException("ADMIN solo puede gestionar RECEPTIONIST");
        if (staff.getId().equals(callerId) && (changesRole || deactivates))
            throw new BusinessException("No puedes cambiar tu propio rol ni desactivarte");
        if (staff.getRole() == Role.OWNER && (changesRole || deactivates))
            throw new BusinessException("El OWNER no se puede degradar ni desactivar");
        if (request.fullName() != null) staff.getAccount().setFullName(request.fullName().trim());
        if (request.role() != null) staff.setRole(request.role());
        if (request.active() != null) staff.setActive(request.active());
        return staffMapper.toResponse(staffRepository.save(staff));
    }

    private Staff getStaffOrThrowById(Long staffId) {
        return staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + staffId));
    }
}
