package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PromoType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreatePromoRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Type is required")
    private PromoType type;

    private Double discountPercent;

    @NotNull(message = "Start time is required")
    private LocalDateTime startsAt;

    @NotNull(message = "Expiry time is required")
    private LocalDateTime expiresAt;
}