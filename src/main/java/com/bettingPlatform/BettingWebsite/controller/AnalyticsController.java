package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.AdminPrincipal;
import com.bettingPlatform.BettingWebsite.dto.ApiResponse;
import com.bettingPlatform.BettingWebsite.dto.MatchAnalyticsResponse;
import com.bettingPlatform.BettingWebsite.service.AiAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AiAnalyticsService aiAnalyticsService;

    // ─────────────────────────────────────────────────────────────
    // Match Analytics — accepts only the internal game UUID.
    // The service resolves the fixture ID and all API IDs internally
    // from the games_table record.
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/match")
    public ResponseEntity<ApiResponse<MatchAnalyticsResponse>> getMatchAnalytics(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam UUID gameId) {

        log.info("[ANALYTICS][MATCH] Request received → gameId={} requestedBy={}",
                gameId, adminPrincipal.getSellerId());

        try {
            MatchAnalyticsResponse response = aiAnalyticsService.analyzeMatch(gameId);

            log.info("[ANALYTICS][MATCH] Analysis complete → gameId={} outcome={} confidence={}% risk={} requestedBy={}",
                    gameId,
                    response.getPredictedOutcome(),
                    response.getConfidencePercent(),
                    response.getRiskLevel(),
                    adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Match analytics", response));

        } catch (IllegalArgumentException e) {
            log.warn("[ANALYTICS][MATCH] Game not found or invalid → gameId={} reason={} requestedBy={}",
                    gameId, e.getMessage(), adminPrincipal.getSellerId());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Game not found: " + e.getMessage()));

        } catch (RuntimeException e) {
            log.warn("[ANALYTICS][MATCH] Analysis failed → gameId={} reason={} requestedBy={}",
                    gameId, e.getMessage(), adminPrincipal.getSellerId());
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Analytics unavailable: " + e.getMessage()));

        } catch (Exception e) {
            log.error("[ANALYTICS][MATCH] Unexpected error → gameId={} reason={} requestedBy={}",
                    gameId, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to run match analytics"));
        }
    }
}