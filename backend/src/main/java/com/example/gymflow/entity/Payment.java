package com.example.gymflow.entity;

import com.example.gymflow.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment received in a cash session for a membership sale.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_session_id")
    private CashSession cashSession;

    @Column(nullable = false)
    private String planName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    private String reference;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_id")
    private Staff receivedBy;

    @Column(nullable = false, updatable = false)
    private Instant paidAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean voided = false;

    private String voidReason;

    private Instant voidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by_id")
    private Staff voidedBy;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey;
}
