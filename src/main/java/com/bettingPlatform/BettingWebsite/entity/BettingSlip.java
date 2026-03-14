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
@Table(name = "betting_slips_table")
public class BettingSlip {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // ── Linked game (optional — slip might cover multiple games) ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private Game game;

    // ── Slip details ──────────────────────────────────────────────
    private String bookmaker;        // "SportyBet", "Bet9ja", "1xBet" etc
    private String bookingCode;      // e.g. "8W1HLE"
    private String description;      // e.g. "10-game accumulator"
    private Double totalOdds;
    private String imageUrl;         // uploaded slip screenshot

    @Enumerated(EnumType.STRING)
    private PredictionType type;     // FREE or VIP

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SlipStatus status = SlipStatus.ACTIVE;

    @Builder.Default
    private boolean published = false;

    private LocalDateTime validUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}