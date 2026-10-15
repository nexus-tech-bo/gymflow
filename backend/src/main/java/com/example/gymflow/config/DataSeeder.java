package com.example.gymflow.config;

import com.example.gymflow.config.tenant.TenantContext;
import com.example.gymflow.entity.*;
import com.example.gymflow.enums.CashSessionStatus;
import com.example.gymflow.enums.CheckInDenialReason;
import com.example.gymflow.enums.CheckInMethod;
import com.example.gymflow.enums.CheckInResult;
import com.example.gymflow.enums.PaymentMethod;
import com.example.gymflow.enums.Role;
import com.example.gymflow.repository.*;
import com.example.gymflow.service.SchemaProvisioningService;
import com.example.gymflow.service.support.MembershipRules;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Seeds one demo gym with staff, plans, members, memberships, payments and check-ins.
 * Dev profile only: every account uses the password {@code password123}.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final GymRepository gymRepository;
    private final StaffRepository staffRepository;
    private final PlanRepository planRepository;
    private final MemberRepository memberRepository;
    private final MemberCardTokenRepository memberCardTokenRepository;
    private final CashSessionRepository cashSessionRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final CheckInRepository checkInRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;
    private final SchemaProvisioningService schemaProvisioningService;
    private final Clock clock;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (gymRepository.count() > 0) return;

        var tx = new TransactionTemplate(transactionManager);
        Gym gym = tx.execute(_ -> gymRepository.save(Gym.builder().name("Iron Gym").build()));
        var schemaName = TenantContext.schemaOf(gym.getId());
        schemaProvisioningService.createTenantSchema(schemaName);

        try {
            TenantContext.setCurrentTenant(schemaName);
            tx.executeWithoutResult(_ -> seedIronGym(gym, passwordEncoder.encode("password123")));
        } finally {
            TenantContext.clear();
        }
    }

    private void seedIronGym(Gym gym, String pwd) {
        Staff owner = seedStaff(gym, "Carlos Dueño", "owner@irongym.com", pwd, Role.OWNER);
        seedStaff(gym, "Ana Admin", "admin@irongym.com", pwd, Role.ADMIN);
        Staff reception = seedStaff(gym, "Rosa Recepción", "recepcion@irongym.com", pwd, Role.RECEPTIONIST);

        Plan daily = seedPlan("Diario", 1, "10.00");
        Plan monthly = seedPlan("Mensual", 30, "100.00");
        Plan quarterly = seedPlan("Trimestral", 90, "270.00");

        Member juan = seedMember("Juan Pérez", "70000001", "999111001");
        Member maria = seedMember("María Gómez", "70000002", "999111002");
        Member luis = seedMember("Luis Torres", "70000003", "999111003");
        Member sofia = seedMember("Sofía Ramos", "70000004", "999111004");
        seedMember("Pedro Castillo", "70000005", null);

        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);

        CashSession lastMonth = cashSessionRepository.save(CashSession.builder()
                .status(CashSessionStatus.CLOSED)
                .openedAt(now.minus(Duration.ofDays(40)))
                .openedBy(owner)
                .openingAmount(new BigDecimal("50.00"))
                .closedAt(now.minus(Duration.ofDays(40)).plus(Duration.ofHours(10)))
                .closedBy(owner)
                .countedCash(new BigDecimal("150.00"))
                .build());
        CashSession current = cashSessionRepository.save(CashSession.builder()
                .openedAt(now.minus(Duration.ofHours(3)))
                .openedBy(reception)
                .openingAmount(new BigDecimal("100.00"))
                .build());

        // expired
        seedMembership(luis, monthly, lastMonth, owner, PaymentMethod.CASH, today.minusDays(40));
        // active, expires in 3 days (shows on dashboard)
        seedMembership(maria, monthly, current, reception, PaymentMethod.QR, today.minusDays(26));
        // active, long
        seedMembership(juan, quarterly, current, reception, PaymentMethod.QR, today.minusDays(10));
        // today only
        seedMembership(sofia, daily, current, reception, PaymentMethod.CASH, today);

        seedCheckIn(juan, now.minus(Duration.ofHours(2)));
        seedCheckIn(maria, now.minus(Duration.ofHours(1)));
        seedCheckIn(sofia, now.minus(Duration.ofMinutes(30)));
        checkInRepository.save(CheckIn.builder()
                .member(luis)
                .method(CheckInMethod.DNI)
                .result(CheckInResult.DENIED)
                .reason(CheckInDenialReason.EXPIRED)
                .checkedAt(now.minus(Duration.ofMinutes(15)))
                .build());
    }

    private Staff seedStaff(Gym gym, String name, String email, String pwd, Role role) {
        return staffRepository.save(Staff.builder()
                .account(Account.builder().gym(gym).fullName(name).email(email).password(pwd).build())
                .role(role)
                .build());
    }

    private Plan seedPlan(String name, int days, String price) {
        return planRepository.save(Plan.builder().name(name).durationDays(days).price(new BigDecimal(price)).build());
    }

    private Member seedMember(String name, String dni, String phone) {
        Member member = memberRepository.save(Member.builder()
                .fullName(name)
                .dni(dni)
                .phone(phone)
                .qrToken(UUID.randomUUID())
                .build());
        memberCardTokenRepository.save(new MemberCardToken(member.getQrToken(), TenantContext.currentGymId()));
        return member;
    }

    private void seedMembership(Member member, Plan plan, CashSession session, Staff staff, PaymentMethod method, LocalDate start) {
        Payment payment = paymentRepository.save(Payment.builder()
                .member(member)
                .cashSession(session)
                .planName(plan.getName())
                .amount(plan.getPrice())
                .method(method)
                .receivedBy(staff)
                .paidAt(session.getOpenedAt().plus(Duration.ofMinutes(30)))
                .idempotencyKey(UUID.randomUUID())
                .build());
        membershipRepository.save(Membership.builder()
                .member(member)
                .plan(plan)
                .payment(payment)
                .planName(plan.getName())
                .price(plan.getPrice())
                .startDate(start)
                .endDate(MembershipRules.endDate(start, plan.getDurationDays()))
                .build());
    }

    private void seedCheckIn(Member member, Instant at) {
        checkInRepository.save(CheckIn.builder()
                .member(member)
                .method(CheckInMethod.QR)
                .result(CheckInResult.ALLOWED)
                .checkedAt(at)
                .build());
    }
}
