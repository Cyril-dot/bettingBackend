package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PromoType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePromoRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Type is required")
    private PromoType type;

    private Double discountPercent;

    // Accept as String to avoid @ModelAttribute LocalDateTime parsing issues
    // Parsed manually in AdminDashboardService using LocalDateTime.parse()
    @NotBlank(message = "Start time is required")
    private String startsAt;

    @NotBlank(message = "Expiry time is required")
    private String expiresAt;
}