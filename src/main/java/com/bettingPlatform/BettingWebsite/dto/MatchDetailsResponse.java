package com.bettingPlatform.BettingWebsite.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatchDetailsResponse {
    private String fixtureId;
    private String fixture;
    private String events;
    private String lineups;
    private String statistics;
}