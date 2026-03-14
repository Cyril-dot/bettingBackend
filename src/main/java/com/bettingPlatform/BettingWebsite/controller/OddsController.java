package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.AdminPrincipal;
import com.bettingPlatform.BettingWebsite.dto.ApiResponse;
import com.bettingPlatform.BettingWebsite.service.OddsApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * ADMIN — Odds data endpoints.
 * Wraps OddsApiClient with structured logging and consistent ApiResponse shape.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/odds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OddsController {

    private final OddsApiClient oddsApiClient;

    // ─────────────────────────────────────────────────────────────
    // Sports
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/odds/sports
     * All sports currently in season.
     */
    @GetMapping("/sports")
    public ResponseEntity<ApiResponse<JsonNode>> getInSeasonSports(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {

        log.info("[ODDS][SPORTS] Fetching in-season sports → requestedBy={}", adminPrincipal.getSellerId());

        try {
            JsonNode data = oddsApiClient.getInSeasonSports();
            log.info("[ODDS][SPORTS] In-season sports fetched → requestedBy={}", adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("In-season sports", data));
        } catch (Exception e) {
            log.error("[ODDS][SPORTS] Failed to fetch sports → reason={} requestedBy={}",
                    e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch sports: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Odds
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/odds/sport?sportKey=soccer_epl&region=uk
     * H2H + totals odds for a sport.
     */
    @GetMapping("/sport")
    public ResponseEntity<ApiResponse<JsonNode>> getOddsForSport(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam String sportKey,
            @RequestParam(defaultValue = "uk") String region) {

        log.info("[ODDS][SPORT] Fetching odds → sport={} region={} requestedBy={}",
                sportKey, region, adminPrincipal.getSellerId());

        try {
            JsonNode data = oddsApiClient.getOddsForSport(sportKey, region);
            log.info("[ODDS][SPORT] Odds fetched → sport={} region={} requestedBy={}",
                    sportKey, region, adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Odds for " + sportKey, data));
        } catch (Exception e) {
            log.error("[ODDS][SPORT] Failed to fetch odds → sport={} reason={} requestedBy={}",
                    sportKey, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch odds: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/admin/odds/sport/full?sportKey=soccer_epl&region=uk
     * H2H + spreads + totals odds for a sport.
     */
    @GetMapping("/sport/full")
    public ResponseEntity<ApiResponse<JsonNode>> getFullOddsForSport(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam String sportKey,
            @RequestParam(defaultValue = "uk") String region) {

        log.info("[ODDS][SPORT] Fetching full odds → sport={} region={} requestedBy={}",
                sportKey, region, adminPrincipal.getSellerId());

        try {
            JsonNode data = oddsApiClient.getFullOddsForSport(sportKey, region);
            log.info("[ODDS][SPORT] Full odds fetched → sport={} region={} requestedBy={}",
                    sportKey, region, adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Full odds for " + sportKey, data));
        } catch (Exception e) {
            log.error("[ODDS][SPORT] Failed to fetch full odds → sport={} reason={} requestedBy={}",
                    sportKey, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch full odds: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/admin/odds/event/{eventId}?sportKey=soccer_epl&region=uk
     * Odds for a specific event.
     */
    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<JsonNode>> getOddsForEvent(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable String eventId,
            @RequestParam(defaultValue = "soccer_epl") String sportKey,
            @RequestParam(defaultValue = "uk") String region) {

        log.info("[ODDS][EVENT] Fetching event odds → eventId={} sport={} region={} requestedBy={}",
                eventId, sportKey, region, adminPrincipal.getSellerId());

        try {
            JsonNode data = oddsApiClient.getOddsForEvent(sportKey, eventId, region);
            log.info("[ODDS][EVENT] Event odds fetched → eventId={} requestedBy={}",
                    eventId, adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Odds for event " + eventId, data));
        } catch (Exception e) {
            log.error("[ODDS][EVENT] Failed to fetch event odds → eventId={} reason={} requestedBy={}",
                    eventId, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch event odds: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/odds/events?sportKey=soccer_epl
     * Upcoming events for a sport.
     */
    @GetMapping("/events")
    public ResponseEntity<ApiResponse<JsonNode>> getEvents(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam String sportKey) {

        log.info("[ODDS][EVENTS] Fetching events → sport={} requestedBy={}",
                sportKey, adminPrincipal.getSellerId());

        try {
            JsonNode data = oddsApiClient.getEvents(sportKey);
            log.info("[ODDS][EVENTS] Events fetched → sport={} requestedBy={}",
                    sportKey, adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Events for " + sportKey, data));
        } catch (Exception e) {
            log.error("[ODDS][EVENTS] Failed to fetch events → sport={} reason={} requestedBy={}",
                    sportKey, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch events: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Scores
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/odds/scores/live?sportKey=soccer_epl
     * Live scores for a sport.
     */
    @GetMapping("/scores/live")
    public ResponseEntity<ApiResponse<JsonNode>> getLiveScores(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam String sportKey) {

        log.info("[ODDS][SCORES] Fetching live scores → sport={} requestedBy={}",
                sportKey, adminPrincipal.getSellerId());

        try {
            JsonNode data = oddsApiClient.getLiveScores(sportKey);
            log.info("[ODDS][SCORES] Live scores fetched → sport={} requestedBy={}",
                    sportKey, adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Live scores for " + sportKey, data));
        } catch (Exception e) {
            log.error("[ODDS][SCORES] Failed to fetch live scores → sport={} reason={} requestedBy={}",
                    sportKey, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch live scores: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/admin/odds/scores?sportKey=soccer_epl&daysFrom=1
     * Recent + live scores. daysFrom=1 includes yesterday's results.
     */
    @GetMapping("/scores")
    public ResponseEntity<ApiResponse<JsonNode>> getScores(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam String sportKey,
            @RequestParam(defaultValue = "1") int daysFrom) {

        log.info("[ODDS][SCORES] Fetching scores → sport={} daysFrom={} requestedBy={}",
                sportKey, daysFrom, adminPrincipal.getSellerId());

        try {
            JsonNode data = oddsApiClient.getScores(sportKey, daysFrom);
            log.info("[ODDS][SCORES] Scores fetched → sport={} daysFrom={} requestedBy={}",
                    sportKey, daysFrom, adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Scores for " + sportKey, data));
        } catch (Exception e) {
            log.error("[ODDS][SCORES] Failed to fetch scores → sport={} reason={} requestedBy={}",
                    sportKey, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch scores: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Markets
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/odds/event/{eventId}/markets?sportKey=soccer_epl&region=uk
     * All available markets for a specific event.
     */
    @GetMapping("/event/{eventId}/markets")
    public ResponseEntity<ApiResponse<JsonNode>> getEventMarkets(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable String eventId,
            @RequestParam(defaultValue = "soccer_epl") String sportKey,
            @RequestParam(defaultValue = "uk") String region) {

        log.info("[ODDS][MARKETS] Fetching markets → eventId={} sport={} region={} requestedBy={}",
                eventId, sportKey, region, adminPrincipal.getSellerId());

        try {
            JsonNode data = oddsApiClient.getEventMarkets(sportKey, eventId, region);
            log.info("[ODDS][MARKETS] Markets fetched → eventId={} requestedBy={}",
                    eventId, adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Markets for event " + eventId, data));
        } catch (Exception e) {
            log.error("[ODDS][MARKETS] Failed to fetch markets → eventId={} reason={} requestedBy={}",
                    eventId, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch markets: " + e.getMessage()));
        }
    }
}