package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.service.ApiFootballClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/booking")
public class TestController {

    private final ApiFootballClient apiFootballClient;

    @GetMapping("/admin/test-fixtures")
    public ResponseEntity<String> testFixtures() {
        String today = LocalDate.now().toString();
        StringBuilder results = new StringBuilder();

        for (int season : new int[]{2024, 2025}) {
            try {
                JsonNode response = apiFootballClient.getFixturesByDate(today, 2, season);
                results.append("=== UCL season ").append(season).append(" ===\n");
                results.append(response.toPrettyString()).append("\n\n");
            } catch (Exception e) {
                results.append("UCL season ").append(season)
                        .append(" ERROR: ").append(e.getMessage()).append("\n\n");
            }
        }

        for (int season : new int[]{2024, 2025}) {
            try {
                JsonNode response = apiFootballClient.getFixturesByDate(today, 39, season);
                results.append("=== EPL season ").append(season).append(" ===\n");
                results.append(response.toPrettyString()).append("\n\n");
            } catch (Exception e) {
                results.append("EPL season ").append(season)
                        .append(" ERROR: ").append(e.getMessage()).append("\n\n");
            }
        }

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body(results.toString());
    }
}
