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

    // Linked game data
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

    // ← ADD THESE
    private Double homeWinOdds;
    private Double drawOdds;
    private Double awayWinOdds;
    private Double over15Odds;
    private Double over25Odds;
    private Double over35Odds;
    private Double under25Odds;
}