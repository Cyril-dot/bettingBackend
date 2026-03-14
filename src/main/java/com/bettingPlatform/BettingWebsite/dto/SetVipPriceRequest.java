package com.bettingPlatform.BettingWebsite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetVipPriceRequest {

    @NotNull(message = "Price is required")
    private Double price;

    @NotBlank(message = "Currency is required")
    private String currency;

    private String description;
}