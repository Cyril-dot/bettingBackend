package com.bettingPlatform.BettingWebsite.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GameStatsResponse {
    private long totalGames;
    private long publishedGames;
    private long vipOnlyGames;
    private long upcomingGames;
    private long liveGames;
    private long finishedGames;
}