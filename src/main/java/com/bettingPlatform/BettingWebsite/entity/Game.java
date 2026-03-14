package com.bettingPlatform.BettingWebsite.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "games_table")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // ── From API-Football ─────────────────────────────────────────
    private String externalFixtureId;
    private String homeTeam;
    private String awayTeam;
    private String league;
    private String country;
    private String leagueLogo;
    private String homeLogo;
    private String awayLogo;
    private LocalDateTime kickoffTime;

    // ── Live match info ───────────────────────────────────────────
    private Integer homeScore;
    private Integer awayScore;
    private Integer elapsedMinutes;
    private String matchPeriod;  // "1H", "HT", "2H", "FT" etc

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private GameStatus status = GameStatus.UPCOMING;

    // ── Admin controls ────────────────────────────────────────────
    @Builder.Default
    private boolean published = false;  // admin publishes game to platform

    // Add this field to your existing Game.java
    @Builder.Default
    private boolean vipOnly = false;  // ← admin marks game as VIP only

    @Builder.Default
    private boolean featured = false;   // admin features on homepage

    // ── Odds snapshot (from The Odds API) ─────────────────────────
    private Double homeWinOdds;
    private Double drawOdds;
    private Double awayWinOdds;
    private Double over25Odds;
    private Double under25Odds;
    private Double over15Odds;
    private Double over35Odds;
    private String oddsBookmaker;

    // ── Predictions linked to this game ──────────────────────────
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Prediction> predictions;

    // ── Betting slips linked to this game ─────────────────────────
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BettingSlip> bettingSlips;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}