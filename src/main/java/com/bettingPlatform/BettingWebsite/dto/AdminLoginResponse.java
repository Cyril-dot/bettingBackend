package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AdminLoginResponse {

    private UUID adminId;
    private String fullName;
    private String email;
    private Role role;
    private String token;
    private String refreshToken;
}