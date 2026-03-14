package com.bettingPlatform.BettingWebsite.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class VipPriceResponse {
    private UUID id;
    private Double price;
    private String currency;
    private String description;
}