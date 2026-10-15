package com.example.gymflow.entity;

import com.example.gymflow.enums.CheckInDenialReason;
import com.example.gymflow.enums.CheckInMethod;
import com.example.gymflow.enums.CheckInResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A recorded check-in attempt of an identified member (allowed or denied).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "check_ins")
public class CheckIn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckInMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckInResult result;

    @Enumerated(EnumType.STRING)
    private CheckInDenialReason reason;

    @Column(nullable = false, updatable = false)
    private Instant checkedAt;
}
