package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.GameStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GameResponse {
    private UUID id;
    private String externalFixtureId;
    private String homeTeam;
    private String awayTeam;
    private String league;
    private String country;
    private String leagueLogo;
    private String homeLogo;
    private String awayLogo;
    private LocalDateTime kickoffTime;
    private Integer homeScore;
    private Integer awayScore;
    private Integer elapsedMinutes;
    private String matchPeriod;
    private GameStatus status;
    private boolean published;
    private boolean featured;
    private Double homeWinOdds;
    private Double drawOdds;
    private Double awayWinOdds;
    private Double over25Odds;
    private Double under25Odds;
    private Double over15Odds;
    private Double over35Odds;
    private String oddsBookmaker;
    // Add this field to your existing GameResponse.java
    private boolean vipOnly;
}