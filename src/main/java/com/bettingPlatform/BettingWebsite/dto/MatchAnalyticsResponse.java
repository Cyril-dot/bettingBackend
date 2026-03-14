package com.bettingPlatform.BettingWebsite.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatchAnalyticsResponse {
    private int fixtureId;
    private double homeWinRate;
    private double awayWinRate;
    private double drawRate;
    private double homeAvgGoalsScored;
    private double awayAvgGoalsScored;
    private double homeAvgGoalsConceded;
    private double awayAvgGoalsConceded;
    private double bttsRate;
    private double over25Rate;
    private String predictedOutcome;
    private double confidencePercent;
    private String suggestedTip;
    private String riskLevel;
    private String aiInsight; // ✅ AI-generated expert reasoning from ASI:One + web search
}