package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PromoType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PromoResponse {
    private UUID id;
    private String title;
    private String description;
    private String imageUrl;
    private PromoType type;
    private Double discountPercent;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private boolean active;
}