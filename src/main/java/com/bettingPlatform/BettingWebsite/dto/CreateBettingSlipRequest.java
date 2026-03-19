package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PredictionType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for creating or updating a BettingSlip.
 *
 * Supports both:
 *   (A) Legacy single-game slips  — gameId + odds fields
 *   (B) Multi-game booking codes  — selections + totalSelections + deadline
 *
 * The admin game-codes page sends this as multipart/form-data with:
 *   bookmaker, bookingCode, type, published, description, totalOdds,
 *   selections (JSON string), totalSelections, deadline, validUntil, image (file)
 */
@Data
public class CreateBettingSlipRequest {

    // ── Core fields (always required) ─────────────────────────────────────────
    private String bookmaker;
    private String bookingCode;
    private String description;
    private Double totalOdds;
    private PredictionType type;
    private boolean published;
    private LocalDateTime validUntil;

    // ── Legacy single-game link (optional) ────────────────────────────────────
    /** UUID of a Game entity — links this slip to a single tracked match */
    private UUID gameId;

    // ── Multi-game booking code fields (new) ──────────────────────────────────
    /**
     * JSON array string containing full details of every game selection.
     * Populated automatically when fetching a booking code from Sportybet/Betway.
     *
     * Example element:
     * {
     *   "homeTeam":         "Roma",
     *   "awayTeam":         "Bologna",
     *   "league":           "UEFA Europa League",
     *   "country":          "Europe",
     *   "sport":            "Football",
     *   "market":           "1X2",
     *   "outcome":          "Home",
     *   "odds":             1.76,
     *   "kickoffTime":      "2026-03-20 20:00 UTC",
     *   "kickoffTimestamp": 1742504400000,
     *   "matchStatus":      "Not start",
     *   "score":            "",
     *   "playedTime":       "",
     *   "statusCode":       0,
     *   "bookingStatus":    "Booked",
     *   "isWinning":        false,
     *   "eventId":          "sr:match:12345",
     *   "gameId":           "67890"
     * }
     */
    private String selections;

    /**
     * Total number of game selections in this slip.
     * Used for quick display without parsing the full JSON.
     */
    private Integer totalSelections;

    /**
     * Booking code expiry datetime string from Sportybet.
     * e.g. "2026-03-31T01:00:00.000+00:00"
     * Stored as a plain String to avoid timezone parsing complexity.
     */
    private String deadline;
}