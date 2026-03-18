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
     * Stored in DB with NOT NULL constraint — always true on insert.
     * Satisfies the existing DB column without needing a migration.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Computed at read-time — NOT persisted (@Transient).
     * Returns true only if now is between startsAt and expiresAt.
     * Jackson serializes this as "active" in JSON, which is what
     * the frontend chip reads — so the stored column is just a DB placeholder.
     */
    @Transient
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return startsAt != null && expiresAt != null
                && !now.isBefore(startsAt)
                && now.isBefore(expiresAt);
    }
}