package com.bettingPlatform.BettingWebsite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BettingSlip entity.
 *
 * Supports two slip types:
 *
 * (A) Legacy single-game slip
 *     game → links to a Game entity
 *     description contains prediction keywords (HOME_WIN, OVER_2_5 etc.)
 *     used by auto-settle logic
 *
 * (B) Multi-game booking code slip (new)
 *     selections   → TEXT column storing JSON array of all game details
 *     totalSelections → count of games
 *     deadline     → Sportybet code expiry string
 *     game field is null for these slips
 */
@Entity
@Table(name = "betting_slips_table")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BettingSlip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Bookmaker info ─────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String bookmaker;

    @Column(nullable = false)
    private String bookingCode;

    // ── Odds ───────────────────────────────────────────────────────────────────
    private Double totalOdds;

    // ── Content ────────────────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    // ── Multi-game fields (booking code slips) ─────────────────────────────────

    /**
     * JSON array of all game selections fetched from the bookmaker.
     * Each element has: homeTeam, awayTeam, league, country, sport,
     * market, outcome, odds, kickoffTime, kickoffTimestamp, matchStatus,
     * score, playedTime, statusCode, bookingStatus, isWinning, eventId, gameId
     *
     * Stored as TEXT — parsed by frontend to render individual game rows.
     */
    @Column(columnDefinition = "TEXT")
    private String selections;

    /**
     * Number of game selections in this slip.
     * Denormalized for fast display without JSON parsing.
     */
    @Column(name = "total_selections")
    private Integer totalSelections;

    /**
     * Booking code expiry datetime string from Sportybet.
     * Stored as plain String to avoid timezone complexity.
     * e.g. "2026-03-31T01:00:00.000+00:00"
     */
    @Column(name = "deadline")
    private String deadline;

    // ── Status & type ──────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PredictionType type;

    @Enumerated(EnumType.STRING)
    private SlipStatus status;

    @Column(nullable = false)
    private boolean published;

    private LocalDateTime validUntil;

    // ── Timestamps ─────────────────────────────────────────────────────────────
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ── Legacy single-game link (optional) ────────────────────────────────────
    /**
     * Links this slip to a single tracked Game entity.
     * Used by the auto-settle feature to determine WON/LOST based on match result.
     * Null for multi-game booking-code slips.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private Game game;
}