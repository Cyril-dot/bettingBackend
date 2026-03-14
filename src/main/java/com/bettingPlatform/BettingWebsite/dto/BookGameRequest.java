package com.bettingPlatform.BettingWebsite.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class BookGameRequest {

    @NotNull(message = "Game ID is required")
    private UUID gameId;

    private boolean published = true;
    private boolean featured  = false;
    private boolean vipOnly   = false;   // ← NEW

    private Double homeWinOdds;
    private Double drawOdds;
    private Double awayWinOdds;
    private Double over25Odds;
    private Double under25Odds;
    private Double over15Odds;
    private Double over35Odds;
}