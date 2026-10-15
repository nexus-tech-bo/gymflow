package com.example.gymflow.service.impl;

import com.example.gymflow.config.tenant.TenantContext;
import com.example.gymflow.dto.auth.AuthResponse;
import com.example.gymflow.dto.auth.LoginRequest;
import com.example.gymflow.dto.auth.RegisterGymRequest;
import com.example.gymflow.entity.Account;
import com.example.gymflow.entity.Gym;
import com.example.gymflow.entity.Staff;
import com.example.gymflow.enums.Role;
import com.example.gymflow.exception.BusinessException;
import com.example.gymflow.exception.UnauthorizedException;
import com.example.gymflow.mapper.StaffMapper;
import com.example.gymflow.repository.AccountRepository;
import com.example.gymflow.repository.GymRepository;
import com.example.gymflow.repository.StaffRepository;
import com.example.gymflow.security.JwtUtil;
import com.example.gymflow.service.AuthService;
import com.example.gymflow.service.SchemaProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;

/**
 * Switches tenants programmatically, so it has no class-level transaction: each tenant gets its own
 * {@link TransactionTemplate} execution after {@link TenantContext} is set.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final String INVALID_CREDENTIALS = "Credenciales inválidas";
    /** Hash compared against when the email is unknown; same algorithm and cost as {@code PasswordEncoderConfig}. */
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("gymflow-timing-dummy");

    private final GymRepository gymRepository;
    private final AccountRepository accountRepository;
    private final StaffRepository staffRepository;
    private final StaffMapper staffMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SchemaProvisioningService schemaProvisioningService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public AuthResponse registerGym(RegisterGymRequest request) {
        var email = normalizeEmail(request.email());
        if (accountRepository.existsByEmail(email))
            throw new BusinessException("El email ya está registrado");
        var tx = new TransactionTemplate(transactionManager);
        var gym = tx.execute(_ -> gymRepository.save(Gym.builder().name(request.gymName().trim()).build()));
        var schemaName = TenantContext.schemaOf(gym.getId());
        try {
            schemaProvisioningService.createTenantSchema(schemaName);
            TenantContext.setCurrentTenant(schemaName);
            var owner = tx.execute(_ -> staffRepository.save(Staff.builder()
                    .account(Account.builder()
                            .gym(gym)
                            .fullName(request.ownerName().trim())
                            .email(email)
                            .password(passwordEncoder.encode(request.password()))
                            .build())
                    .role(Role.OWNER)
                    .build()));
            return toAuthResponse(owner);
        } catch (RuntimeException e) {
            // e.g. a concurrent registration with the same email wins the unique constraint: undo this gym
            TenantContext.clear();
            try {
                schemaProvisioningService.dropTenantSchema(schemaName);
                tx.executeWithoutResult(_ -> gymRepository.deleteById(gym.getId()));
            } catch (RuntimeException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        var found = accountRepository.findByEmail(normalizeEmail(request.email()));
        // always pay the BCrypt cost so response time does not reveal whether the email exists
        var passwordMatches = passwordEncoder.matches(request.password(), found.map(Account::getPassword).orElse(DUMMY_HASH));
        if (found.isEmpty() || !passwordMatches)
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        var account = found.get();
        try {
            TenantContext.setCurrentTenant(TenantContext.schemaOf(account.getGym().getId()));
            var staff = staffRepository.findByAccountId(account.getId())
                    .filter(Staff::isActive)
                    .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
            return toAuthResponse(staff);
        } finally {
            TenantContext.clear();
        }
    }

    private AuthResponse toAuthResponse(Staff staff) {
        var account = staff.getAccount();
        var token = jwtUtil.generate(staff.getId(), staff.getRole().name(), account.getGym().getId(), account.getFullName());
        return new AuthResponse(token, jwtUtil.getExpirationMs() / 1000, staffMapper.toMeResponse(staff));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
