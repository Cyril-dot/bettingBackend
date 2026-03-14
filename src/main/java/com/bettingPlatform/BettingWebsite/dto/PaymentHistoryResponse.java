package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PaymentPurpose;
import com.bettingPlatform.BettingWebsite.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentHistoryResponse {
    private UUID id;
    private String reference;
    private Double amount;
    private String currency;
    private PaymentStatus status;
    private PaymentPurpose purpose;
    private String channel;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}