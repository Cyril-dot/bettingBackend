package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PaymentPurpose;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InitiatePaymentRequest {

    @NotNull(message = "Purpose is required")
    private PaymentPurpose purpose;   // "VIP_SUBSCRIPTION"
}