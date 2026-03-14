package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AdminResponse {

    private UUID id;
    private String fullName;
    private String email;
    private Role role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}