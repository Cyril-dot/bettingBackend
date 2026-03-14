package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PredictionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateBettingSlipRequest {

    // optional — link to a specific game
    private UUID gameId;

    @NotBlank(message = "Bookmaker is required")
    private String bookmaker;

    @NotBlank(message = "Booking code is required")
    private String bookingCode;

    private String description;
    private Double totalOdds;

    @NotNull(message = "Type is required")
    private PredictionType type;    // FREE or VIP

    private LocalDateTime validUntil;
    private boolean published = false;
}