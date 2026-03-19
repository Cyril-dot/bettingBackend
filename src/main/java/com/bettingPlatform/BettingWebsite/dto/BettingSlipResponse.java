package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PredictionType;
import com.bettingPlatform.BettingWebsite.entity.SlipStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a BettingSlip.
 *
 * Includes both legacy single-game fields (homeTeam, awayTeam, league, etc.)
 * and new multi-game booking code fields (selections, totalSelections, deadline).
 *
 * Frontend logic:
 *   if selections is non-null and non-empty → render as multi-game accumulator
 *   else if homeTeam is non-null            → render as legacy single-game slip
 */
@Data
@Builder
public class BettingSlipResponse {

    // ── Identity ───────────────────────────────────────────────────────────────
    private UUID id;

    // ── Bookmaker ──────────────────────────────────────────────────────────────
    private String bookmaker;
    private String bookingCode;

    // ── Content ────────────────────────────────────────────────────────────────
    private String description;
    private Double totalOdds;
    private String imageUrl;

    // ── Multi-game booking code fields ─────────────────────────────────────────
    /** JSON array string of all game selections — parsed by frontend */
    private String selections;

    /** Number of games in the slip — shown without parsing JSON */
    private Integer totalSelections;

    /** Booking code expiry from Sportybet e.g. "2026-03-31T01:00:00.000+00:00" */
    private String deadline;

    // ── Status & type ──────────────────────────────────────────────────────────
    private PredictionType type;
    private SlipStatus status;
    private boolean published;
    private LocalDateTime validUntil;

    // ── Timestamps ─────────────────────────────────────────────────────────────
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Legacy single-game fields (populated when game != null) ───────────────
    private UUID gameId;
    private String homeTeam;
    private String awayTeam;
    private String league;
    private String homeLogo;
    private String awayLogo;
    private LocalDateTime kickoffTime;
    private String gameStatus;
    private Integer homeScore;
    private Integer awayScore;
    private Double homeWinOdds;
    private Double drawOdds;
    private Double awayWinOdds;
    private Double over15Odds;
    private Double over25Odds;
    private Double over35Odds;
    private Double under25Odds;
}