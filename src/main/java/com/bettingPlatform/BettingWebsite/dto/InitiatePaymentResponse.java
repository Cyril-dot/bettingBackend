package com.bettingPlatform.BettingWebsite.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InitiatePaymentResponse {
    private String reference;
    private String authorizationUrl;  // redirect user here to pay
    private String accessCode;
    private Double amount;
    private String currency;
    private String email;
}