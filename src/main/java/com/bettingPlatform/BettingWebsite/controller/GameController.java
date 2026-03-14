package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.UserPrincipal;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.service.GameService;
import com.bettingPlatform.BettingWebsite.service.ScheduledFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class GameController {

    private final GameService            gameService;
    private final ScheduledFetchService  scheduledFetchService;
    private final SimpMessagingTemplate  messagingTemplate;

    // ─────────────────────────────────────────────────────────────
    // Games
    // ─────────────────────────────────────────────────────────────

    /** GET /api/v1/games/today */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getTodayGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] Today's games → userId={}", userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getRegisteredUserTodayGames();
            return ResponseEntity.ok(ApiResponse.success("Today's games", games));
        } catch (Exception e) {
            log.error("[GAMES] Failed today → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch today's games"));
        }
    }

    /** GET /api/v1/games/tomorrow */
    @GetMapping("/tomorrow")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getTomorrowGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] Tomorrow's games → userId={}", userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getTomorrowGames();
            return ResponseEntity.ok(ApiResponse.success("Tomorrow's games", games));
        } catch (Exception e) {
            log.error("[GAMES] Failed tomorrow → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch tomorrow's games"));
        }
    }

    /** GET /api/v1/games/day-after-tomorrow */
    @GetMapping("/day-after-tomorrow")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getDayAfterTomorrowGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] Day-after-tomorrow games → userId={}", userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getDayAfterTomorrowGames();
            return ResponseEntity.ok(ApiResponse.success("Day after tomorrow's games", games));
        } catch (Exception e) {
            log.error("[GAMES] Failed day-after-tomorrow → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch day after tomorrow's games"));
        }
    }

    /** GET /api/v1/games/next-week */
    @GetMapping("/next-week")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getNextWeekGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] Next week games → userId={}", userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getNextWeekGames();
            return ResponseEntity.ok(ApiResponse.success("Next 7 days games", games));
        } catch (Exception e) {
            log.error("[GAMES] Failed next-week → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch next week's games"));
        }
    }

    /** GET /api/v1/games/by-date?date=2026-03-15 */
    @GetMapping("/by-date")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getGamesByDate(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("[GAMES] By date → date={} userId={}", date, userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getGamesByDate(date);
            return ResponseEntity.ok(ApiResponse.success("Games for " + date, games));
        } catch (Exception e) {
            log.error("[GAMES] Failed by-date → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch games for date: " + date));
        }
    }

    /** GET /api/v1/games/live
     *  Returns all currently live games from DB.
     *  Clients should ALSO subscribe to WebSocket topic /topic/live-scores for real-time pushes.
     */
    @GetMapping("/live")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getLiveGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] Live games → userId={}", userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getRegisteredUserLiveGames();
            return ResponseEntity.ok(ApiResponse.success("Live games", games));
        } catch (Exception e) {
            log.error("[GAMES] Failed live → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch live games"));
        }
    }

    /** POST /api/v1/games/live/refresh */
    @PostMapping("/live/refresh")
    public ResponseEntity<ApiResponse<List<GameResponse>>> refreshLiveScores(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] Manual live refresh → userId={}", userPrincipal.getUserId());
        try {
            scheduledFetchService.fetchLiveScores();
            List<GameResponse> games = gameService.getRegisteredUserLiveGames();
            log.info("[GAMES] Live refresh done → count={} userId={}", games.size(), userPrincipal.getUserId());
            return ResponseEntity.ok(ApiResponse.success("Live scores refreshed", games));
        } catch (Exception e) {
            log.error("[GAMES] Live refresh failed → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to refresh live scores"));
        }
    }

    /** GET /api/v1/games/ws-info */
    @GetMapping("/ws-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWebSocketInfo(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] WS info → userId={}", userPrincipal.getUserId());
        Map<String, Object> info = Map.of(
                "endpoint",      "/ws",
                "topics", Map.of(
                        "liveScores",  "/topic/live-scores",
                        "gamesUpdate", "/topic/games-update"
                ),
                "refreshInterval", 30000,
                "description", "Subscribe to /topic/live-scores for real-time score updates. " +
                        "Subscribe to /topic/games-update for full game list refreshes."
        );
        return ResponseEntity.ok(ApiResponse.success("WebSocket info", info));
    }

    /** GET /api/v1/games/featured */
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getFeaturedGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] Featured → userId={}", userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Featured games", gameService.getFeaturedGames()));
        } catch (Exception e) {
            log.error("[GAMES] Failed featured → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch featured games"));
        }
    }

    /** GET /api/v1/games/featured/free */
    @GetMapping("/featured/free")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getFeaturedFreeGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] Featured free → userId={}", userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Featured free games", gameService.getFeaturedFreeGames()));
        } catch (Exception e) {
            log.error("[GAMES] Failed featured free → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch featured free games"));
        }
    }

    /** GET /api/v1/games/upcoming */
    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getUpcomingGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] Upcoming → userId={}", userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Upcoming games", gameService.getUpcomingPublicGames()));
        } catch (Exception e) {
            log.error("[GAMES] Failed upcoming → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch upcoming games"));
        }
    }

    /** GET /api/v1/games/league?league=Premier League */
    @GetMapping("/league")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getGamesByLeague(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam String league) {
        log.info("[GAMES] By league → league='{}' userId={}", league, userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Games for " + league, gameService.getGamesByLeague(league)));
        } catch (Exception e) {
            log.error("[GAMES] Failed league → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch games by league"));
        }
    }

    /** GET /api/v1/games/history */
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getPreviousGames(
            @RequestParam(defaultValue = "7")  int days,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] History → days={} page={} size={} userId={}", days, page, size, userPrincipal.getUserId());
        try {
            List<GameResponse> games = gameService.getPreviousGames(days, page, size);
            return ResponseEntity.ok(ApiResponse.<List<GameResponse>>builder()
                    .success(true)
                    .message("Previous games retrieved successfully")
                    .data(games)
                    .build());
        } catch (Exception e) {
            log.error("[GAMES] Failed history → userId={} reason={}", userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch game history"));
        }
    }

    /** GET /api/v1/games/search?keyword=arsenal */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<GameResponse>>> searchGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam String keyword) {
        log.info("[GAMES] Search → keyword='{}' userId={}", keyword, userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Search results for '" + keyword + "'", gameService.searchGames(keyword)));
        } catch (Exception e) {
            log.error("[GAMES] Failed search → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to search games"));
        }
    }

    /** GET /api/v1/games/top5 */
    @GetMapping("/top5")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getTop5LeagueGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES] Top5 → userId={}", userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Top 5 league games", gameService.getTop5LeagueGames()));
        } catch (Exception e) {
            log.error("[GAMES] Failed top5 → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch top 5 league games"));
        }
    }

    /** GET /api/v1/games/top5/search?keyword=arsenal */
    @GetMapping("/top5/search")
    public ResponseEntity<ApiResponse<List<GameResponse>>> searchTop5LeagueGames(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam String keyword) {
        log.info("[GAMES] Top5 search → keyword='{}' userId={}", keyword, userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Top 5 search results for '" + keyword + "'", gameService.searchTop5LeagueGames(keyword)));
        } catch (Exception e) {
            log.error("[GAMES] Failed top5 search → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to search top 5 league games"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Slips
    // ─────────────────────────────────────────────────────────────

    /** GET /api/v1/games/slips/free */
    @GetMapping("/slips/free")
    public ResponseEntity<ApiResponse<List<BettingSlipResponse>>> getFreeSlips(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("[GAMES][SLIPS] Free slips → userId={}", userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Free betting slips", gameService.getRegisteredUserFreeSlips()));
        } catch (Exception e) {
            log.error("[GAMES][SLIPS] Failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch free slips"));
        }
    }

    /** GET /api/v1/games/slips/{slipId} */
    @GetMapping("/slips/{slipId}")
    public ResponseEntity<ApiResponse<BettingSlipResponse>> getSlipById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID slipId) {
        log.info("[GAMES][SLIPS] Single slip → slipId={} userId={}", slipId, userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Slip details", gameService.getSlipById(slipId)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Slip not found: " + slipId));
        } catch (Exception e) {
            log.error("[GAMES][SLIPS] Failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch slip details"));
        }
    }

    /** PUT /api/v1/games/slips/{slipId} */
    @PutMapping(value = "/slips/{slipId}", consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse<BettingSlipResponse>> updateSlip(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID slipId,
            @RequestPart("slip") CreateBettingSlipRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        log.info("[GAMES][SLIPS] Update → slipId={} userId={}", slipId, userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Slip updated successfully", gameService.updateSlip(slipId, request, image)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Slip not found: " + slipId));
        } catch (Exception e) {
            log.error("[GAMES][SLIPS] Update failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update slip"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // NOTE: /{id} must stay LAST to avoid swallowing named paths
    // ─────────────────────────────────────────────────────────────

    /** GET /api/v1/games/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GameResponse>> getGame(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id) {
        log.info("[GAMES] Single game → gameId={} userId={}", id, userPrincipal.getUserId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Game", gameService.getGame(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Game not found: " + id));
        } catch (Exception e) {
            log.error("[GAMES] Failed → gameId={} reason={}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch game"));
        }
    }
}