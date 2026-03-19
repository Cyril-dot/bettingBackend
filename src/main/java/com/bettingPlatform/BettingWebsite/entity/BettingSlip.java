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
    private String bookmaker;        // "Sportybet Ghana", "Betway Ghana" etc
    private String bookingCode;      // e.g. "8RF5L8"
    private String description;      // human-readable summary of all games
    private Double totalOdds;        // combined odds of all selections
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

    // ── Multi-game slip fields ─────────────────────────────────────

    /**
     * JSON array storing full details of every game in this slip.
     *
     * Stored as TEXT in DB — each element is one game with all fields:
     *
     * [
     *   {
     *     "homeTeam":         "Club Necaxa",
     *     "awayTeam":         "Club Tijuana de Caliente",
     *     "league":           "Liga MX, Clausura",
     *     "country":          "Mexico",
     *     "sport":            "Football",
     *     "market":           "1X2",
     *     "outcome":          "Home",
     *     "odds":             2.20,
     *     "kickoffTime":      "2026-03-21 01:00 UTC",
     *     "kickoffTimestamp": 1774054800000,
     *     "matchStatus":      "Not start",
     *     "score":            "",
     *     "playedTime":       "",
     *     "statusCode":       0,
     *     "bookingStatus":    "Booked",
     *     "isWinning":        false,
     *     "eventId":          "sr:match:66856082",
     *     "gameId":           "20199"
     *   },
     *   ...
     * ]
     *
     * Frontend usage:
     *   const games = JSON.parse(slip.selections);
     *   games.forEach(game => renderGameRow(game));
     */
    @Column(columnDefinition = "TEXT")
    private String selections;

    /**
     * Number of games in this slip e.g. 4
     * Used to show "4 games" badge on the slip card without parsing JSON
     */
    @Column(name = "total_selections")
    private Integer totalSelections;

    /**
     * Booking code expiry deadline from Sportybet
     * e.g. "2026-03-31T01:00:00.000+00:00"
     */
    @Column(name = "deadline")
    private String deadline;
}