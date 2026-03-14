package com.bettingPlatform.BettingWebsite.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentVerificationResponse {
    private String reference;
    private String status;           // "success", "failed"
    private Double amount;
    private String currency;
    private String message;
    private boolean vipActivated;
    private LocalDateTime vipExpiresAt;
}