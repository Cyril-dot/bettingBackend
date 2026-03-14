package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserSummaryResponse {

    private UUID id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private Role role;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;  // pulled from LastLogin entity
}