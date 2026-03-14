package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.SlipStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSlipStatusRequest {

    @NotNull(message = "Status is required")
    private SlipStatus status;
}