package com.example.gymflow.service.impl;

import com.example.gymflow.config.tenant.TenantContext;
import com.example.gymflow.dto.auth.LoginRequest;
import com.example.gymflow.dto.auth.RegisterGymRequest;
import com.example.gymflow.entity.Account;
import com.example.gymflow.entity.Gym;
import com.example.gymflow.entity.Staff;
import com.example.gymflow.enums.Role;
import com.example.gymflow.exception.UnauthorizedException;
import com.example.gymflow.mapper.StaffMapperImpl;
import com.example.gymflow.repository.AccountRepository;
import com.example.gymflow.repository.GymRepository;
import com.example.gymflow.repository.StaffRepository;
import com.example.gymflow.security.JwtUtil;
import com.example.gymflow.service.SchemaProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {
    @Mock GymRepository gymRepository;
    @Mock AccountRepository accountRepository;
    @Mock StaffRepository staffRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SchemaProvisioningService schemaProvisioningService;
    @Mock PlatformTransactionManager transactionManager;

    JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-123456", 28_800_000L);
    AuthServiceImpl service;
    Account account;
    Staff staff;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(gymRepository, accountRepository, staffRepository, new StaffMapperImpl(),
                passwordEncoder, jwtUtil, schemaProvisioningService, transactionManager);
        account = Account.builder().id(20L).fullName("Ana Pérez").email("ana@gym.pe").password("hash")
                .gym(Gym.builder().id(1L).name("Gym Lima").build()).build();
        staff = Staff.builder().id(4L).role(Role.OWNER).account(account).build();
    }

    @Test
    void login_should_normalizeEmail_andReturnToken() {
        when(accountRepository.findByEmail("ana@gym.pe")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secreta123", "hash")).thenReturn(true);
        when(staffRepository.findByAccountId(20L)).thenReturn(Optional.of(staff));

        var response = service.login(new LoginRequest("  Ana@Gym.PE ", "secreta123"));

        assertThat(response.expiresIn()).isEqualTo(28_800);
        assertThat(response.user().gymName()).isEqualTo("Gym Lima");
        var claims = jwtUtil.parse(response.accessToken()).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo("4");
        assertThat(claims.get("gymId", Long.class)).isEqualTo(1L);
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void login_should_rejectInactiveStaff() {
        staff.setActive(false);
        when(accountRepository.findByEmail("ana@gym.pe")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secreta123", "hash")).thenReturn(true);
        when(staffRepository.findByAccountId(20L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@gym.pe", "secreta123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Credenciales inválidas");
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void login_should_stillCheckPassword_when_emailUnknown() {
        when(accountRepository.findByEmail("nadie@gym.pe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nadie@gym.pe", "secreta123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Credenciales inválidas");
        verify(passwordEncoder).matches(eq("secreta123"), anyString());
        verifyNoInteractions(staffRepository);
    }

    @Test
    void login_should_rejectWrongPassword() {
        when(accountRepository.findByEmail("ana@gym.pe")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@gym.pe", "mala")))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(staffRepository);
    }

    @Test
    void registerGym_should_dropSchemaAndDeleteGym_when_ownerSaveFails() {
        when(gymRepository.save(any())).thenReturn(Gym.builder().id(7L).name("Nuevo").build());
        when(passwordEncoder.encode("secreta123")).thenReturn("hash");
        var conflict = new DataIntegrityViolationException("accounts_email_key");
        when(staffRepository.save(any())).thenThrow(conflict);

        assertThatThrownBy(() -> service.registerGym(new RegisterGymRequest("Nuevo", "Ana", "ana@gym.pe", "secreta123")))
                .isSameAs(conflict);
        verify(schemaProvisioningService).createTenantSchema("gym_7");
        verify(schemaProvisioningService).dropTenantSchema("gym_7");
        verify(gymRepository).deleteById(7L);
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
