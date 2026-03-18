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
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private PromoType type;

    private Double discountPercent;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Computed at read-time — no stored column needed.
     * A promo is active if the current time is between startsAt and expiresAt.
     * This means you never need a scheduler to flip an "active" flag.
     *
     * @Transient = not persisted to DB, just calculated on the fly.
     */
    @Transient
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return startsAt != null && expiresAt != null
                && !now.isBefore(startsAt)
                && now.isBefore(expiresAt);
    }
}