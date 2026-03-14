package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LoginResponse {

    private String token;
    private String refreshToken;
    private UUID userId;
    private String fullName;
    private String email;
    private Role role;
}