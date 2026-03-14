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
@Table(name = "promos_table")
public class Promo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String title;
    private String description;
    private String imageUrl;       // optional promo banner

    @Enumerated(EnumType.STRING)
    private PromoType type;        // DISCOUNT, FREE_VIP, ANNOUNCEMENT

    private Double discountPercent; // e.g. 50.0 for 50% off VIP
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}