package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.UserPrincipal;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * VIP USER — Full access including VIP-only games and slips.
 * Service layer enforces active VIP subscription check on every method.
 * Spring Security enforces USER role at the controller level.
 * Base path: /api/v1/vip/games
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vip/games")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class VipGameController {

    private final GameService gameService;

    // ─────────────────────────────────────────────────────────────
    // Games
    // ─────────────────────────────────────────────────────────────

    /** GET /api/v1/vip/games/today */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getVipTodayGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("[VIP][GAMES] Today's games requested → userId={}", userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getVipTodayGames(userPrincipal);
            log.info("[VIP][GAMES] Today's games returned → count={} userId={}", games.size(), userPrincipal.getUserId());
            return ResponseEntity.ok(ApiResponse.success("VIP today's games", games));
        } catch (RuntimeException e) {
            log.warn("[VIP][GAMES] Access denied → userId={} reason={}", userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[VIP][GAMES] Failed → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to fetch VIP games"));
        }
    }

    /** GET /api/v1/vip/games/live */
    @GetMapping("/live")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getVipLiveGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("[VIP][GAMES] Live games requested → userId={}", userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getVipLiveGames(userPrincipal);
            log.info("[VIP][GAMES] Live games returned → count={} userId={}", games.size(), userPrincipal.getUserId());
            return ResponseEntity.ok(ApiResponse.success("VIP live games", games));
        } catch (RuntimeException e) {
            log.warn("[VIP][GAMES] Access denied → userId={} reason={}", userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[VIP][GAMES] Failed → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to fetch VIP live games"));
        }
    }

    /** GET /api/v1/vip/games/live/vip-only */
    @GetMapping("/live/vip-only")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getVipOnlyLiveGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("[VIP][GAMES] VIP-only live requested → userId={}", userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getVipOnlyLiveGames(userPrincipal);
            log.info("[VIP][GAMES] VIP-only live returned → count={} userId={}", games.size(), userPrincipal.getUserId());
            return ResponseEntity.ok(ApiResponse.success("VIP-only live games", games));
        } catch (RuntimeException e) {
            log.warn("[VIP][GAMES] Access denied → userId={} reason={}", userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[VIP][GAMES] Failed → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to fetch VIP-only live games"));
        }
    }

    /** GET /api/v1/vip/games/upcoming */
    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getUpcomingVipGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("[VIP][GAMES] Upcoming requested → userId={}", userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getUpcomingVipGames(userPrincipal);
            log.info("[VIP][GAMES] Upcoming returned → count={} userId={}", games.size(), userPrincipal.getUserId());
            return ResponseEntity.ok(ApiResponse.success("Upcoming VIP games", games));
        } catch (RuntimeException e) {
            log.warn("[VIP][GAMES] Access denied → userId={} reason={}", userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[VIP][GAMES] Failed → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to fetch upcoming VIP games"));
        }
    }

    /** GET /api/v1/vip/games/featured */
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getFeaturedVipGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("[VIP][GAMES] Featured requested → userId={}", userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getFeaturedVipGames(userPrincipal);
            log.info("[VIP][GAMES] Featured returned → count={} userId={}", games.size(), userPrincipal.getUserId());
            return ResponseEntity.ok(ApiResponse.success("Featured VIP games", games));
        } catch (RuntimeException e) {
            log.warn("[VIP][GAMES] Access denied → userId={} reason={}", userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[VIP][GAMES] Failed → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to fetch featured VIP games"));
        }
    }

    /** GET /api/v1/vip/games/league?league=Premier League */
    @GetMapping("/league")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getVipGamesByLeague(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam String league) {

        log.info("[VIP][GAMES] By league requested → league='{}' userId={}", league, userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getVipGamesByLeague(league, userPrincipal);
            log.info("[VIP][GAMES] By league returned → league='{}' count={} userId={}", league, games.size(), userPrincipal.getUserId());
            return ResponseEntity.ok(ApiResponse.success("VIP games for " + league, games));
        } catch (RuntimeException e) {
            log.warn("[VIP][GAMES] Access denied → userId={} reason={}", userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[VIP][GAMES] Failed → league='{}' userId={} reason={}", league, userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to fetch VIP games by league"));
        }
    }

    /**
     * GET /api/v1/vip/games/history?days=7&page=0&size=20
     *
     * FIX: Was completely missing from the original controller.
     * Frontend dashboard calls /vip/games/history for VIP users
     * but the endpoint did not exist → 404 for all VIP history requests.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getVipPreviousGames(
            @RequestParam(defaultValue = "7")  int days,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("[VIP][GAMES] History requested → days={} page={} size={} userId={}",
                days, page, size, userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getVipPreviousGames(days, page, size, userPrincipal);
            log.info("[VIP][GAMES] History returned → count={} userId={}", games.size(), userPrincipal.getUserId());
            return ResponseEntity.ok(
                    ApiResponse.<List<GameResponse>>builder()
                            .success(true)
                            .message("VIP previous games retrieved successfully")
                            .data(games)
                            .build()
            );
        } catch (RuntimeException e) {
            log.warn("[VIP][GAMES] Access denied for history → userId={} reason={}", userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[VIP][GAMES] Failed to fetch history → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to fetch VIP game history"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Slips
    // ─────────────────────────────────────────────────────────────

    /** GET /api/v1/vip/games/slips */
    @GetMapping("/slips")
    public ResponseEntity<ApiResponse<List<BettingSlipResponse>>> getVipSlips(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("[VIP][SLIPS] VIP slips requested → userId={}", userPrincipal.getUserId());
        try {
            List<BettingSlipResponse> slips = gameService.getVipSlips(userPrincipal);
            log.info("[VIP][SLIPS] VIP slips returned → count={} userId={}", slips.size(), userPrincipal.getUserId());
            return ResponseEntity.ok(ApiResponse.success("VIP betting slips", slips));
        } catch (RuntimeException e) {
            log.warn("[VIP][SLIPS] Access denied → userId={} reason={}", userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[VIP][SLIPS] Failed → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to fetch VIP slips"));
        }
    }

    /** GET /api/v1/vip/games/slips/bookmaker?bookmaker=Betway */
    @GetMapping("/slips/bookmaker")
    public ResponseEntity<ApiResponse<List<BettingSlipResponse>>> getVipSlipsByBookmaker(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam String bookmaker) {

        log.info("[VIP][SLIPS] By bookmaker requested → bookmaker='{}' userId={}", bookmaker, userPrincipal.getUserId());
        try {
            List<BettingSlipResponse> slips = gameService.getVipSlipsByBookmaker(bookmaker, userPrincipal);
            log.info("[VIP][SLIPS] By bookmaker returned → bookmaker='{}' count={} userId={}", bookmaker, slips.size(), userPrincipal.getUserId());
            return ResponseEntity.ok(ApiResponse.success("VIP slips for " + bookmaker, slips));
        } catch (RuntimeException e) {
            log.warn("[VIP][SLIPS] Access denied → userId={} reason={}", userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[VIP][SLIPS] Failed → bookmaker='{}' userId={} reason={}", bookmaker, userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to fetch VIP slips by bookmaker"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Match Details
    // ─────────────────────────────────────────────────────────────

    /** GET /api/v1/vip/games/match/{externalFixtureId}/details */
    @GetMapping("/match/{externalFixtureId}/details")
    public ResponseEntity<ApiResponse<MatchDetailsResponse>> getVipMatchDetails(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String externalFixtureId) {

        log.info("[VIP][MATCH] Details requested → fixtureId={} userId={}", externalFixtureId, userPrincipal.getUserId());
        try {
            MatchDetailsResponse details = gameService.getVipMatchDetails(externalFixtureId, userPrincipal);
            log.info("[VIP][MATCH] Details returned → fixtureId={} userId={}", externalFixtureId, userPrincipal.getUserId());
            return ResponseEntity.ok(ApiResponse.success("Match details", details));
        } catch (RuntimeException e) {
            log.warn("[VIP][MATCH] Access denied → fixtureId={} userId={} reason={}", externalFixtureId, userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[VIP][MATCH] Failed → fixtureId={} userId={} reason={}", externalFixtureId, userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error("Failed to fetch match details"));
        }
    }
}