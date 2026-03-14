package com.bettingPlatform.BettingWebsite.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "vip_subscriptions_table")
public class VipSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime activatedAt;
    private LocalDateTime expiresAt;  // activatedAt + 24 hours

    @Builder.Default
    private boolean active = true;

    private Double amountPaid;
    private String transactionRef;
}