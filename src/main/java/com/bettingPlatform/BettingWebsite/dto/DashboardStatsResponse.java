package com.bettingPlatform.BettingWebsite.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsResponse {
    private long totalUsers;
    private long activeVipUsers;
    private long totalPredictions;
    private long wonPredictions;
    private long lostPredictions;
    private double winRate;
    private long totalGamesPosted;
    private long activeFreeSlips;
    private long activeVipSlips;
}