package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PUBLIC — No authentication required.
 * Teaser content only: 3 games, 2 live, 2 slips, stripped of odds/codes.
 * Designed to drive registrations.
 * Base path: /api/v1/public
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicGameController {

    private final GameService gameService;

    /**
     * GET /api/v1/public/today
     * 3 games preview — no odds, no scores. Teaser only.
     */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getPublicTodayGames() {

        log.info("[PUBLIC][GAMES] Today's games requested");

        try {
            List<GameResponse> games = gameService.getPublicTodayGames();
            log.info("[PUBLIC][GAMES] Today's games returned → count={}", games.size());
            return ResponseEntity.ok(ApiResponse.success("Today's games preview", games));
        } catch (Exception e) {
            log.error("[PUBLIC][GAMES] Failed to fetch today's games → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch games"));
        }
    }

    /**
     * GET /api/v1/public/live
     * 2 live games preview — stripped response.
     */
    @GetMapping("/live")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getPublicLiveGames() {

        log.info("[PUBLIC][GAMES] Live games requested");

        try {
            List<GameResponse> games = gameService.getPublicLiveGames();
            log.info("[PUBLIC][GAMES] Live games returned → count={}", games.size());
            return ResponseEntity.ok(ApiResponse.success("Live games preview", games));
        } catch (Exception e) {
            log.error("[PUBLIC][GAMES] Failed to fetch live games → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch live games"));
        }
    }

    /**
     * GET /api/v1/public/starting-soon
     * Games kicking off in the next 15 minutes — 3 max, stripped.
     */
    @GetMapping("/starting-soon")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getGamesStartingSoon() {

        log.info("[PUBLIC][GAMES] Starting-soon games requested");

        try {
            List<GameResponse> games = gameService.getGamesStartingSoon();
            log.info("[PUBLIC][GAMES] Starting-soon returned → count={}", games.size());
            return ResponseEntity.ok(ApiResponse.success("Games starting soon", games));
        } catch (Exception e) {
            log.error("[PUBLIC][GAMES] Failed to fetch starting-soon → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch games starting soon"));
        }
    }

    /**
     * GET /api/v1/public/slips
     * 2 free slips preview — booking code masked as ****.
     */
    @GetMapping("/slips")
    public ResponseEntity<ApiResponse<List<BettingSlipResponse>>> getPublicFreeSlips() {

        log.info("[PUBLIC][SLIPS] Free slips preview requested");

        try {
            List<BettingSlipResponse> slips = gameService.getPublicFreeSlips();
            log.info("[PUBLIC][SLIPS] Slips preview returned → count={}", slips.size());
            return ResponseEntity.ok(ApiResponse.success("Free slips preview", slips));
        } catch (Exception e) {
            log.error("[PUBLIC][SLIPS] Failed to fetch slips preview → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch slips"));
        }
    }

    /**
     * GET /api/v1/public/history
     * Public past games (finished, non-VIP).
     * FIX: Was mapped as /games/history which resolved to /api/v1/public/games/history (wrong).
     *      Correct path is now /api/v1/public/history
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getPreviousGames(
            @RequestParam(defaultValue = "7")  int days,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("[PUBLIC][GAMES] Public history requested → days={} page={} size={}", days, page, size);

        try {
            List<GameResponse> games = gameService.getPreviousGames(days, page, size);
            log.info("[PUBLIC][GAMES] Public history returned → count={}", games.size());
            return ResponseEntity.ok(
                    ApiResponse.<List<GameResponse>>builder()
                            .success(true)
                            .message("Previous games retrieved successfully")
                            .data(games)
                            .build()
            );
        } catch (Exception e) {
            log.error("[PUBLIC][GAMES] Failed to fetch public history → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch game history"));
        }
    }
}