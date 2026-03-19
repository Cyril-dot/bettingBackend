package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PredictionType;
import com.bettingPlatform.BettingWebsite.entity.SlipStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BettingSlipResponse {

    private UUID id;
    private String bookmaker;
    private String bookingCode;
    private String description;
    private Double totalOdds;
    private String imageUrl;
    private PredictionType type;
    private SlipStatus status;
    private boolean published;
    private LocalDateTime validUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Linked single game (legacy — for manually created slips) ──
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

    // ── Multi-game slip fields (populated when created from booking code) ──

    /**
     * JSON string of all game selections inside this slip.
     *
     * Frontend parses this to render each game row when user clicks the slip:
     *   const games = JSON.parse(slip.selections);
     *
     * Each game object contains:
     *   homeTeam, awayTeam, league, country, sport,
     *   market, outcome, odds,
     *   kickoffTime, kickoffTimestamp,
     *   matchStatus, score, playedTime, statusCode,
     *   bookingStatus, isWinning,
     *   eventId, gameId
     *
     * null for manually created slips (use homeTeam/awayTeam fields instead)
     */
    private String selections;

    /**
     * Number of games in this slip e.g. 4
     * Show as badge on slip card: "4 games"
     * null for manually created single-game slips
     */
    private Integer totalSelections;

    /**
     * Booking code expiry deadline e.g. "2026-03-31T01:00:00.000+00:00"
     * null for manually created slips
     */
    private String deadline;
}