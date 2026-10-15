package com.example.gymflow.service.impl;

import com.example.gymflow.dto.staff.StaffRequest;
import com.example.gymflow.dto.staff.StaffUpdateRequest;
import com.example.gymflow.entity.Account;
import com.example.gymflow.entity.Gym;
import com.example.gymflow.entity.Staff;
import com.example.gymflow.enums.Role;
import com.example.gymflow.exception.BadRequestException;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.exception.ForbiddenException;
import com.example.gymflow.mapper.StaffMapperImpl;
import com.example.gymflow.repository.AccountRepository;
import com.example.gymflow.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StaffServiceImpl")
class StaffServiceImplTest {
    @Mock StaffRepository staffRepository;
    @Mock AccountRepository accountRepository;
    @Mock PasswordEncoder passwordEncoder;

    StaffServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StaffServiceImpl(staffRepository, accountRepository, new StaffMapperImpl(), passwordEncoder);
    }

    private Staff staff(long id, Role role) {
        var staff = Staff.builder().id(id).role(role)
                .account(Account.builder().fullName("User " + id).email(id + "@gym.pe").gym(Gym.builder().id(1L).build()).build())
                .build();
        lenient().when(staffRepository.findById(id)).thenReturn(Optional.of(staff));
        return staff;
    }

    private static StaffRequest newStaff(Role role) {
        return new StaffRequest("Rosa Quispe", " Rosa@Gym.pe ", "inicial123", role);
    }

    @Test
    void createStaff_should_rejectOwnerRole() {
        assertThatThrownBy(() -> service.createStaff(newStaff(Role.OWNER), 1L, "OWNER"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createStaff_should_forbidAdminCreatingAdmin() {
        assertThatThrownBy(() -> service.createStaff(newStaff(Role.ADMIN), 2L, "ADMIN"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createStaff_should_throwConflict_when_emailTaken() {
        when(accountRepository.existsByEmail("rosa@gym.pe")).thenReturn(true);

        assertThatThrownBy(() -> service.createStaff(newStaff(Role.RECEPTIONIST), 1L, "OWNER"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createStaff_should_normalizeEmail_andAttachCallerGym() {
        staff(2L, Role.ADMIN);
        when(passwordEncoder.encode("inicial123")).thenReturn("hash");
        when(staffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createStaff(newStaff(Role.RECEPTIONIST), 2L, "ADMIN");

        assertThat(response.email()).isEqualTo("rosa@gym.pe");
        assertThat(response.role()).isEqualTo(Role.RECEPTIONIST);
    }

    @Test
    void updateStaff_should_forbidAdmin_managingNonReceptionist() {
        staff(3L, Role.ADMIN);

        assertThatThrownBy(() -> service.updateStaffById(3L, new StaffUpdateRequest("X", null, null), 2L, "ADMIN"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateStaff_should_forbidAdmin_promotingReceptionist() {
        staff(4L, Role.RECEPTIONIST);

        assertThatThrownBy(() -> service.updateStaffById(4L, new StaffUpdateRequest(null, Role.ADMIN, null), 2L, "ADMIN"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateStaff_should_rejectSelfDeactivation() {
        staff(2L, Role.ADMIN);

        assertThatThrownBy(() -> service.updateStaffById(2L, new StaffUpdateRequest(null, null, false), 2L, "OWNER"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateStaff_should_rejectSelfRoleChange() {
        staff(1L, Role.OWNER);

        assertThatThrownBy(() -> service.updateStaffById(1L, new StaffUpdateRequest(null, Role.ADMIN, null), 1L, "OWNER"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateStaff_should_rejectDemotingOwner() {
        staff(1L, Role.OWNER);

        assertThatThrownBy(() -> service.updateStaffById(1L, new StaffUpdateRequest(null, Role.ADMIN, null), 9L, "OWNER"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El OWNER no se puede degradar ni desactivar");
    }

    @Test
    void updateStaff_should_applyOnlyNonNullFields() {
        var target = staff(4L, Role.RECEPTIONIST);
        when(staffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateStaffById(4L, new StaffUpdateRequest(null, null, false), 2L, "ADMIN");

        assertThat(response.active()).isFalse();
        assertThat(response.fullName()).isEqualTo("User 4");
        assertThat(target.getRole()).isEqualTo(Role.RECEPTIONIST);
    }
}
