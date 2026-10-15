package com.example.gymflow.entity;

import com.example.gymflow.enums.CashSessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A cash register session (caja). At most one is {@code OPEN} per gym (DB partial unique index).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "cash_sessions")
public class CashSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CashSessionStatus status = CashSessionStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant openedAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by_id")
    private Staff openedBy;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal openingAmount;

    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private Staff closedBy;

    @Column(precision = 10, scale = 2)
    private BigDecimal countedCash;

    private String notes;

    @Version
    private Long version;
}
