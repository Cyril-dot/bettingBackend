package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PredictionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CreatePredictionRequest {

    @NotBlank(message = "League is required")
    private String league;

    @NotBlank(message = "Home team is required")
    private String homeTeam;

    @NotBlank(message = "Away team is required")
    private String awayTeam;

    @NotBlank(message = "Tip is required")
    private String tip;

    @NotNull(message = "Type is required")
    private PredictionType type;

    @NotNull(message = "Match date is required")
    private LocalDate matchDate;

    private boolean published = false;
}