package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.AdminPrincipal;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.service.GameService;
import com.bettingPlatform.BettingWebsite.service.ScheduledFetchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/games")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminGameController {

    private final GameService            gameService;
    private final ScheduledFetchService  scheduledFetchService;
    private final SimpMessagingTemplate  messagingTemplate;

    // ─────────────────────────────────────────────────────────────
    // Game Queries
    // ─────────────────────────────────────────────────────────────

    /** GET /api/v1/admin/games/today */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getTodayGames(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        log.info("[ADMIN][GAMES] Today's games → requestedBy={}", adminPrincipal.getSellerId());
        try {
            List<GameResponse> games = gameService.getTodayGames();
            log.info("[ADMIN][GAMES] Fetched → count={} requestedBy={}", games.size(), adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Today's games", games));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch today's games"));
        }
    }

    /** GET /api/v1/admin/games/tomorrow */
    @GetMapping("/tomorrow")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getTomorrowGames(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        log.info("[ADMIN][GAMES] Tomorrow's games → requestedBy={}", adminPrincipal.getSellerId());
        try {
            List<GameResponse> games = gameService.getTomorrowGames();
            log.info("[ADMIN][GAMES] Tomorrow → count={} requestedBy={}", games.size(), adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Tomorrow's games", games));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Failed tomorrow → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch tomorrow's games"));
        }
    }

    /** GET /api/v1/admin/games/day-after-tomorrow */
    @GetMapping("/day-after-tomorrow")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getDayAfterTomorrowGames(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        log.info("[ADMIN][GAMES] Day-after-tomorrow → requestedBy={}", adminPrincipal.getSellerId());
        try {
            List<GameResponse> games = gameService.getDayAfterTomorrowGames();
            log.info("[ADMIN][GAMES] Day-after-tomorrow → count={} requestedBy={}", games.size(), adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Day after tomorrow's games", games));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Failed day-after-tomorrow → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch day after tomorrow's games"));
        }
    }

    /** GET /api/v1/admin/games/next-week */
    @GetMapping("/next-week")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getNextWeekGames(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        log.info("[ADMIN][GAMES] Next week games → requestedBy={}", adminPrincipal.getSellerId());
        try {
            List<GameResponse> games = gameService.getNextWeekGames();
            log.info("[ADMIN][GAMES] Next week → count={} requestedBy={}", games.size(), adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Next 7 days games", games));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Failed next-week → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch next week's games"));
        }
    }

    /** GET /api/v1/admin/games/by-date?date=2026-03-15 */
    @GetMapping("/by-date")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getGamesByDate(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("[ADMIN][GAMES] By date → date={} requestedBy={}", date, adminPrincipal.getSellerId());
        try {
            List<GameResponse> games = gameService.getGamesByDate(date);
            log.info("[ADMIN][GAMES] By date → count={} requestedBy={}", games.size(), adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Games for " + date, games));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Failed by-date → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch games for date: " + date));
        }
    }

    /** GET /api/v1/admin/games/stats */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<GameStatsResponse>> getGameStats(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        log.info("[ADMIN][GAMES] Stats → requestedBy={}", adminPrincipal.getSellerId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Game stats", gameService.getGameStats()));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Stats failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch game stats"));
        }
    }

    /** GET /api/v1/admin/games/{gameId}/details?externalFixtureId=... */
    @GetMapping("/{gameId}/details")
    public ResponseEntity<ApiResponse<MatchDetailsResponse>> getMatchDetails(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam String externalFixtureId) {
        log.info("[ADMIN][GAMES] Match details → fixtureId={} requestedBy={}", externalFixtureId, adminPrincipal.getSellerId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Match details", gameService.getMatchDetails(externalFixtureId)));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Details failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch match details: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Live Score Controls
    // ─────────────────────────────────────────────────────────────

    /** POST /api/v1/admin/games/live/refresh */
    @PostMapping("/live/refresh")
    public ResponseEntity<ApiResponse<List<GameResponse>>> refreshLiveScores(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        log.info("[ADMIN][GAMES] Manual live refresh → requestedBy={}", adminPrincipal.getSellerId());
        try {
            scheduledFetchService.fetchLiveScores();
            List<GameResponse> games = gameService.getTodayGames();
            log.info("[ADMIN][GAMES] Live refresh done → count={} requestedBy={}", games.size(), adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Live scores refreshed", games));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Live refresh failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to refresh live scores"));
        }
    }

    /** POST /api/v1/admin/games/fixtures/refresh */
    @PostMapping("/fixtures/refresh")
    public ResponseEntity<ApiResponse<List<GameResponse>>> refreshFixtures(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        log.info("[ADMIN][GAMES] Manual fixture refresh → requestedBy={}", adminPrincipal.getSellerId());
        try {
            scheduledFetchService.fetchTodayFixtures();
            List<GameResponse> games = gameService.getTodayGames();
            log.info("[ADMIN][GAMES] Fixture refresh done → count={} requestedBy={}", games.size(), adminPrincipal.getSellerId());
            return ResponseEntity.ok(ApiResponse.success("Fixtures refreshed", games));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Fixture refresh failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to refresh fixtures"));
        }
    }

    /** GET /api/v1/admin/games/ws-info */
    @GetMapping("/ws-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWebSocketInfo(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        log.info("[ADMIN][GAMES] WS info → requestedBy={}", adminPrincipal.getSellerId());
        Map<String, Object> info = Map.of(
                "endpoint", "/ws",
                "topics", Map.of(
                        "liveScores",  "/topic/live-scores",
                        "gamesUpdate", "/topic/games-update"
                ),
                "manualRefreshEndpoints", Map.of(
                        "liveScores", "POST /api/v1/admin/games/live/refresh",
                        "fixtures",   "POST /api/v1/admin/games/fixtures/refresh"
                ),
                "schedulerIntervals", Map.of(
                        "liveScores",  "30s",
                        "fixtures",    "30min",
                        "odds",        "3hr"
                )
        );
        return ResponseEntity.ok(ApiResponse.success("WebSocket info", info));
    }

    // ─────────────────────────────────────────────────────────────
    // Game Management
    // ─────────────────────────────────────────────────────────────

    /** POST /api/v1/admin/games/book */
    @PostMapping("/book")
    public ResponseEntity<ApiResponse<GameResponse>> bookGame(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @Valid @RequestBody BookGameRequest request) {
        log.info("[ADMIN][GAMES] Booking → gameId={} requestedBy={}", request.getGameId(), adminPrincipal.getSellerId());
        try {
            GameResponse response = gameService.bookGame(request);
            log.info("[ADMIN][GAMES] Booked → gameId={} requestedBy={}", response.getId(), adminPrincipal.getSellerId());
            messagingTemplate.convertAndSend("/topic/games-update",
                    (Object) Map.of("event", "GAME_BOOKED", "gameId", response.getId()));
            return ResponseEntity.ok(ApiResponse.success("Game booked", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Book failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to book game"));
        }
    }

    /** POST /api/v1/admin/games/book/bulk */
    /** POST /api/v1/admin/games/book/bulk */
    @PostMapping("/book/bulk")
    public ResponseEntity<ApiResponse<List<GameResponse>>> bookMultipleGames(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @Valid @RequestBody List<BookGameRequest> requests) {
        log.info("[ADMIN][GAMES] Bulk book → count={} requestedBy={}", requests.size(), adminPrincipal.getSellerId());
        try {
            List<GameResponse> games = gameService.bookMultipleGames(requests);
            log.info("[ADMIN][GAMES] Bulk done → booked={} requestedBy={}", games.size(), adminPrincipal.getSellerId());
            messagingTemplate.convertAndSend("/topic/games-update",
                    (Object) Map.of("event", "BULK_BOOKED", "count", games.size()));
            return ResponseEntity.ok(ApiResponse.success("Games booked", games));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Bulk failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Bulk book failed: " + e.getMessage()));
        }
    }

    /** PATCH /api/v1/admin/games/{gameId}/unpublish */
    @PatchMapping("/{gameId}/unpublish")
    public ResponseEntity<ApiResponse<GameResponse>> unpublishGame(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID gameId) {
        log.info("[ADMIN][GAMES] Unpublish → gameId={} requestedBy={}", gameId, adminPrincipal.getSellerId());
        try {
            GameResponse response = gameService.unpublishGame(gameId);
            messagingTemplate.convertAndSend("/topic/games-update",
                    (Object) Map.of("event", "GAME_UNPUBLISHED", "gameId", gameId));
            return ResponseEntity.ok(ApiResponse.success("Game unpublished", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Game not found: " + gameId));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Unpublish failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to unpublish game"));
        }
    }

    /** PATCH /api/v1/admin/games/{gameId}/toggle-featured */
    @PatchMapping("/{gameId}/toggle-featured")
    public ResponseEntity<ApiResponse<GameResponse>> toggleFeatured(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID gameId) {
        log.info("[ADMIN][GAMES] Toggle featured → gameId={} requestedBy={}", gameId, adminPrincipal.getSellerId());
        try {
            GameResponse response = gameService.toggleFeatured(gameId);
            messagingTemplate.convertAndSend("/topic/games-update",
                    (Object) Map.of("event", "GAME_FEATURED_TOGGLED", "gameId", gameId, "featured", response.isFeatured()));
            return ResponseEntity.ok(ApiResponse.success("Featured toggled", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Game not found: " + gameId));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Toggle featured failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to toggle featured"));
        }
    }

    /** PATCH /api/v1/admin/games/{gameId}/toggle-vip */
    @PatchMapping("/{gameId}/toggle-vip")
    public ResponseEntity<ApiResponse<GameResponse>> toggleVipOnly(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID gameId) {
        log.info("[ADMIN][GAMES] Toggle VIP → gameId={} requestedBy={}", gameId, adminPrincipal.getSellerId());
        try {
            GameResponse response = gameService.toggleVipOnly(gameId);
            messagingTemplate.convertAndSend("/topic/games-update",
                    (Object) Map.of("event", "GAME_VIP_TOGGLED", "gameId", gameId, "vipOnly", response.isVipOnly()));
            return ResponseEntity.ok(ApiResponse.success("VIP flag toggled", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Game not found: " + gameId));
        } catch (Exception e) {
            log.error("[ADMIN][GAMES] Toggle VIP failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to toggle VIP flag"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Slip Management
    // ─────────────────────────────────────────────────────────────

    /** GET /api/v1/admin/games/slips */
    @GetMapping("/slips")
    public ResponseEntity<ApiResponse<List<BettingSlipResponse>>> getAllSlips(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        log.info("[ADMIN][SLIPS] All slips → requestedBy={}", adminPrincipal.getSellerId());
        try {
            List<BettingSlipResponse> slips = gameService.getAllSlips();
            log.info("[ADMIN][SLIPS] Fetched → count={}", slips.size());
            return ResponseEntity.ok(ApiResponse.success("All betting slips", slips));
        } catch (Exception e) {
            log.error("[ADMIN][SLIPS] Failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch slips"));
        }
    }

    /** GET /api/v1/admin/games/slips/{slipId} */
    @GetMapping("/slips/{slipId}")
    public ResponseEntity<ApiResponse<BettingSlipResponse>> getSlip(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID slipId) {
        log.info("[ADMIN][SLIPS] Single slip → slipId={} requestedBy={}", slipId, adminPrincipal.getSellerId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Slip details", gameService.getSlipById(slipId)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[ADMIN][SLIPS] Failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch slip"));
        }
    }

    /** POST /api/v1/admin/games/slips */
    @PostMapping(value = "/slips", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BettingSlipResponse>> createSlip(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @Valid @ModelAttribute CreateBettingSlipRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        log.info("[ADMIN][SLIPS] Creating → bookmaker={} type={} requestedBy={}",
                request.getBookmaker(), request.getType(), adminPrincipal.getSellerId());
        try {
            BettingSlipResponse response = gameService.createSlipWithImage(request, image);
            log.info("[ADMIN][SLIPS] Created → id={}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Betting slip created", response));
        } catch (IOException e) {
            log.error("[ADMIN][SLIPS] Image upload failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Image upload failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("[ADMIN][SLIPS] Create failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create slip"));
        }
    }

    /** PUT /api/v1/admin/games/slips/{slipId} */
    @PutMapping(value = "/slips/{slipId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BettingSlipResponse>> updateSlip(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID slipId,
            @Valid @ModelAttribute CreateBettingSlipRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        log.info("[ADMIN][SLIPS] Update → slipId={} requestedBy={}", slipId, adminPrincipal.getSellerId());
        try {
            BettingSlipResponse response = gameService.updateSlip(slipId, request, image);
            log.info("[ADMIN][SLIPS] Updated → slipId={}", slipId);
            return ResponseEntity.ok(ApiResponse.success("Slip updated", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Image upload failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("[ADMIN][SLIPS] Update failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update slip"));
        }
    }

    /** POST /api/v1/admin/games/slips/{slipId}/image */
    @PostMapping(value = "/slips/{slipId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BettingSlipResponse>> uploadSlipImage(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID slipId,
            @RequestPart("image") MultipartFile image) {
        log.info("[ADMIN][SLIPS] Image upload → slipId={} requestedBy={}", slipId, adminPrincipal.getSellerId());
        try {
            return ResponseEntity.ok(ApiResponse.success("Slip image uploaded", gameService.uploadSlipImage(slipId, image)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Slip not found: " + slipId));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Image upload failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("[ADMIN][SLIPS] Image upload failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to upload image"));
        }
    }

    /** PATCH /api/v1/admin/games/slips/{slipId}/status */
    @PatchMapping("/slips/{slipId}/status")
    public ResponseEntity<ApiResponse<BettingSlipResponse>> updateSlipStatus(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID slipId,
            @Valid @RequestBody UpdateSlipStatusRequest request) {
        log.info("[ADMIN][SLIPS] Status update → slipId={} status={} requestedBy={}",
                slipId, request.getStatus(), adminPrincipal.getSellerId());
        try {
            BettingSlipResponse response = gameService.updateSlipStatus(slipId, request);
            log.info("[ADMIN][SLIPS] Status updated → slipId={} status={}", slipId, response.getStatus());
            return ResponseEntity.ok(ApiResponse.success("Slip status updated", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Slip not found: " + slipId));
        } catch (Exception e) {
            log.error("[ADMIN][SLIPS] Status update failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update slip status"));
        }
    }

    /** PATCH /api/v1/admin/games/slips/{slipId}/toggle-publish */
    @PatchMapping("/slips/{slipId}/toggle-publish")
    public ResponseEntity<ApiResponse<BettingSlipResponse>> toggleSlipPublish(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID slipId) {
        log.info("[ADMIN][SLIPS] Toggle publish → slipId={} requestedBy={}", slipId, adminPrincipal.getSellerId());
        try {
            BettingSlipResponse response = gameService.toggleSlipPublish(slipId);
            log.info("[ADMIN][SLIPS] Publish toggled → slipId={} published={}", slipId, response.isPublished());
            return ResponseEntity.ok(ApiResponse.success("Slip publish toggled", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Slip not found: " + slipId));
        } catch (Exception e) {
            log.error("[ADMIN][SLIPS] Toggle publish failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to toggle slip publish"));
        }
    }

    /** DELETE /api/v1/admin/games/slips/{slipId} */
    @DeleteMapping("/slips/{slipId}")
    public ResponseEntity<ApiResponse<Void>> deleteSlip(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID slipId) {
        log.info("[ADMIN][SLIPS] Delete → slipId={} requestedBy={}", slipId, adminPrincipal.getSellerId());
        try {
            gameService.deleteSlip(slipId);
            log.info("[ADMIN][SLIPS] Deleted → slipId={}", slipId);
            return ResponseEntity.ok(ApiResponse.success("Slip deleted", null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Slip not found: " + slipId));
        } catch (Exception e) {
            log.error("[ADMIN][SLIPS] Delete failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete slip"));
        }
    }

    /** PATCH /api/v1/admin/games/slips/bulk-settle */
    @PatchMapping("/slips/bulk-settle")
    public ResponseEntity<ApiResponse<List<BettingSlipResponse>>> bulkAutoSettle(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {
        log.info("[ADMIN][SLIPS] Bulk settle → requestedBy={}", adminPrincipal.getSellerId());
        try {
            List<BettingSlipResponse> settled = gameService.bulkAutoSettle();
            log.info("[ADMIN][SLIPS] Bulk settled → count={}", settled.size());
            return ResponseEntity.ok(ApiResponse.success("Bulk settled " + settled.size() + " slips", settled));
        } catch (Exception e) {
            log.error("[ADMIN][SLIPS] Bulk settle failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Bulk settle failed"));
        }
    }

    /** PATCH /api/v1/admin/games/slips/{slipId}/auto-settle */
    @PatchMapping("/slips/{slipId}/auto-settle")
    public ResponseEntity<ApiResponse<BettingSlipResponse>> autoSettleSlip(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID slipId) {
        log.info("[ADMIN][SLIPS] Auto-settle → slipId={} requestedBy={}", slipId, adminPrincipal.getSellerId());
        try {
            BettingSlipResponse response = gameService.autoSettleSlip(slipId);
            log.info("[ADMIN][SLIPS] Settled → slipId={} status={}", slipId, response.getStatus());
            return ResponseEntity.ok(ApiResponse.success("Slip auto-settled → " + response.getStatus(), response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[ADMIN][SLIPS] Auto-settle failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Auto-settle failed"));
        }
    }
}