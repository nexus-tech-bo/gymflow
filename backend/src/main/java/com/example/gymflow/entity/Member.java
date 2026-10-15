package com.example.gymflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A gym member (socio). {@code qrToken} identifies the member card and check-in QR.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "members")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String dni;

    private String phone;

    private String email;

    private LocalDate birthDate;

    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, unique = true)
    private UUID qrToken;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = Instant.now();
    }
}
