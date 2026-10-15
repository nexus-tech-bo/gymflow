package com.example.gymflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A gym (tenant). Lives in {@code public}; its data lives in schema {@code gym_<id>}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "gyms", schema = "public")
public class Gym {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = Instant.now();
    }
}
