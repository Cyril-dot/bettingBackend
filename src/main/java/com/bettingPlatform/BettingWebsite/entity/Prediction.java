package com.bettingPlatform.BettingWebsite.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "predictions_table")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // ── Linked game ───────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private Game game;

    // ── Match info (denormalized for easy display) ────────────────
    private String externalFixtureId;
    private String league;
    private String homeTeam;
    private String awayTeam;
    private String leagueLogo;
    private String homeLogo;
    private String awayLogo;
    private LocalDateTime kickoffTime;

    // ── Prediction details ────────────────────────────────────────
    private String tip;           // e.g. "Over 2.5 Goals"
    private String result;        // "✅" or "❌"
    private Double odds;          // e.g. 1.85

    @Enumerated(EnumType.STRING)
    private PredictionType type;  // FREE, VIP, FREE_SUPER_ODDS, MIDNIGHT_ORACLE

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PredictionStatus status = PredictionStatus.PENDING;

    private LocalDate matchDate;

    @Builder.Default
    private boolean published = false;

    // ── AI analysis ───────────────────────────────────────────────
    private String aiAnalysis;        // ASI:One analysis JSON
    private Double confidenceScore;   // e.g. 72.5
    private String riskLevel;         // LOW, MEDIUM, HIGH

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}