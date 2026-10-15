package com.example.gymflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Global registry from a member QR token to its gym, used by the public member card.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "token")
@Entity
@Table(name = "member_card_tokens", schema = "public")
public class MemberCardToken {
    @Id
    private UUID token;

    @Column(nullable = false)
    private Long gymId;
}
