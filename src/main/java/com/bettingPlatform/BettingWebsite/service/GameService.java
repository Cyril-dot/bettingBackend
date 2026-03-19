package com.bettingPlatform.BettingWebsite.service;

import com.bettingPlatform.BettingWebsite.Config.Security.UserPrincipal;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.entity.*;
import com.bettingPlatform.BettingWebsite.entity.repos.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final GameRepo gameRepo;
    private final BettingSlipRepo bettingSlipRepo;
    private final ApiFootballClient apiFootballClient;
    private final CloudinaryService cloudinaryService;
    private final VipSubscriptionRepo vipSubscriptionRepo;
    private final UserRepo userRepo;
    private final BookingCodeService bookingCodeService;

    private static final ZoneId APP_ZONE = ZoneId.of("UTC");

    private LocalDateTime startOfToday() {
        return LocalDate.now(APP_ZONE).atStartOfDay();
    }

    private LocalDateTime endOfToday() {
        return LocalDate.now(APP_ZONE).atTime(LocalTime.MAX);
    }

    private LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }

    private LocalDateTime endOf(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }

    private record LeagueKey(String country, String league) {}

    @SuppressWarnings("SpellCheckingInspection")
    private static final List<LeagueKey> TOP_5_LEAGUES = List.of(
            new LeagueKey("England", "Premier League"),
            new LeagueKey("France",  "Ligue 1"),
            new LeagueKey("Germany", "Bundesliga"),
            new LeagueKey("Italy",   "Serie A"),
            new LeagueKey("Spain",   "Primera Division")
    );

    private boolean isTop5League(Game game) {
        return TOP_5_LEAGUES.stream().anyMatch(l ->
                l.country().equalsIgnoreCase(game.getCountry()) &&
                        l.league().equalsIgnoreCase(game.getLeague())
        );
    }

    // ═══════════════════════════════════════════════════════════
    // TOP 5
    // ═══════════════════════════════════════════════════════════

    public List<GameResponse> getTop5LeagueGames() {
        return gameRepo.findAll().stream()
                .filter(Game::isPublished)
                .filter(this::isTop5League)
                .map(this::mapGame)
                .collect(Collectors.toList());
    }

    public List<GameResponse> searchTop5LeagueGames(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String search = keyword.toLowerCase();
        return gameRepo.findAll().stream()
                .filter(Game::isPublished)
                .filter(this::isTop5League)
                .filter(g ->
                        g.getHomeTeam().toLowerCase().contains(search) ||
                                g.getAwayTeam().toLowerCase().contains(search) ||
                                g.getLeague().toLowerCase().contains(search) ||
                                g.getCountry().toLowerCase().contains(search)
                )
                .map(this::mapGame)
                .collect(Collectors.toList());
    }

    public List<GameResponse> searchGames(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String search = keyword.toLowerCase();
        return gameRepo.findAll().stream()
                .filter(Game::isPublished)
                .filter(g ->
                        g.getHomeTeam().toLowerCase().contains(search) ||
                                g.getAwayTeam().toLowerCase().contains(search) ||
                                g.getLeague().toLowerCase().contains(search) ||
                                g.getCountry().toLowerCase().contains(search)
                )
                .map(this::mapGame)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — Game Management
    // ═══════════════════════════════════════════════════════════

    public List<GameResponse> getTodayGames() {
        return gameRepo.findAllTodayGames(startOfToday(), endOfToday())
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    public GameResponse bookGame(BookGameRequest request) {
        Game game = gameRepo.findById(request.getGameId())
                .orElseThrow(() -> new RuntimeException("Game not found"));

        game.setPublished(request.isPublished());
        game.setFeatured(request.isFeatured());
        game.setVipOnly(request.isVipOnly());

        if (request.getHomeWinOdds() != null) game.setHomeWinOdds(request.getHomeWinOdds());
        if (request.getDrawOdds()    != null) game.setDrawOdds(request.getDrawOdds());
        if (request.getAwayWinOdds() != null) game.setAwayWinOdds(request.getAwayWinOdds());
        if (request.getOver25Odds()  != null) game.setOver25Odds(request.getOver25Odds());
        if (request.getUnder25Odds() != null) game.setUnder25Odds(request.getUnder25Odds());
        if (request.getOver15Odds()  != null) game.setOver15Odds(request.getOver15Odds());
        if (request.getOver35Odds()  != null) game.setOver35Odds(request.getOver35Odds());

        game.setUpdatedAt(LocalDateTime.now(APP_ZONE));
        Game saved = gameRepo.save(game);
        log.info("✅ Game booked: {} vs {} — Published: {}, VIP: {}",
                saved.getHomeTeam(), saved.getAwayTeam(), saved.isPublished(), saved.isVipOnly());
        return mapGame(saved);
    }

    public List<GameResponse> bookMultipleGames(List<BookGameRequest> requests) {
        return requests.stream()
                .map(this::bookGame)
                .collect(Collectors.toList());
    }

    public GameResponse unpublishGame(UUID gameId) {
        Game game = gameRepo.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        game.setPublished(false);
        game.setUpdatedAt(LocalDateTime.now(APP_ZONE));
        return mapGame(gameRepo.save(game));
    }

    public GameResponse toggleFeatured(UUID gameId) {
        Game game = gameRepo.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        game.setFeatured(!game.isFeatured());
        game.setUpdatedAt(LocalDateTime.now(APP_ZONE));
        return mapGame(gameRepo.save(game));
    }

    public GameResponse toggleVipOnly(UUID gameId) {
        Game game = gameRepo.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        game.setVipOnly(!game.isVipOnly());
        game.setUpdatedAt(LocalDateTime.now(APP_ZONE));
        log.info("👑 Game {} VIP toggled: {}", gameId, game.isVipOnly());
        return mapGame(gameRepo.save(game));
    }

    public MatchDetailsResponse getMatchDetails(String externalFixtureId) {
        int fixtureId    = Integer.parseInt(externalFixtureId);
        JsonNode fixture = apiFootballClient.getFixtureById(fixtureId);
        JsonNode events  = apiFootballClient.getMatchEvents(fixtureId);
        JsonNode lineups = apiFootballClient.getMatchLineups(fixtureId);
        JsonNode stats   = apiFootballClient.getMatchStatistics(fixtureId);
        return MatchDetailsResponse.builder()
                .fixtureId(externalFixtureId)
                .fixture(fixture != null ? fixture.toString() : null)
                .events(events   != null ? events.toString()  : null)
                .lineups(lineups != null ? lineups.toString() : null)
                .statistics(stats != null ? stats.toString()  : null)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — Slip Management
    // ═══════════════════════════════════════════════════════════

    public List<BettingSlipResponse> getAllSlips() {
        return bettingSlipRepo.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapSlip)
                .collect(Collectors.toList());
    }

    public BettingSlipResponse getSlipById(UUID slipId) {
        BettingSlip slip = bettingSlipRepo.findById(slipId)
                .orElseThrow(() -> new RuntimeException("Slip not found: " + slipId));
        return mapSlip(slip);
    }

    public BettingSlipResponse createBettingSlip(CreateBettingSlipRequest request) {
        Game game = null;
        if (request.getGameId() != null) {
            game = gameRepo.findById(request.getGameId()).orElse(null);
        }
        BettingSlip slip = BettingSlip.builder()
                .game(game)
                .bookmaker(request.getBookmaker())
                .bookingCode(request.getBookingCode())
                .description(request.getDescription())
                .totalOdds(request.getTotalOdds())
                .type(request.getType())
                .validUntil(request.getValidUntil())
                .published(request.isPublished())
                .createdAt(LocalDateTime.now(APP_ZONE))
                .updatedAt(LocalDateTime.now(APP_ZONE))
                .build();
        BettingSlip saved = bettingSlipRepo.save(slip);
        log.info("🎫 Slip created: {} — {}", saved.getBookmaker(), saved.getBookingCode());
        return mapSlip(saved);
    }

    public BettingSlipResponse uploadSlipImage(UUID slipId, MultipartFile image) throws IOException {
        BettingSlip slip = bettingSlipRepo.findById(slipId)
                .orElseThrow(() -> new RuntimeException("Slip not found"));
        Map<String, Object> uploadResult = cloudinaryService.uploadImage(image, "bettingPlatform/slips");
        slip.setImageUrl((String) uploadResult.get("secure_url"));
        slip.setUpdatedAt(LocalDateTime.now(APP_ZONE));
        BettingSlip saved = bettingSlipRepo.save(slip);
        log.info("📸 Slip image uploaded: {}", saved.getBookingCode());
        return mapSlip(saved);
    }

    public BettingSlipResponse createSlipWithImage(CreateBettingSlipRequest request, MultipartFile image) throws IOException {
        BettingSlipResponse slip = createBettingSlip(request);
        if (image != null && !image.isEmpty()) {
            return uploadSlipImage(slip.getId(), image);
        }
        return slip;
    }

    public BettingSlipResponse updateSlip(UUID slipId, CreateBettingSlipRequest request, MultipartFile image) throws IOException {
        BettingSlip slip = bettingSlipRepo.findById(slipId)
                .orElseThrow(() -> new RuntimeException("Slip not found: " + slipId));

        if (request.getBookmaker()   != null) slip.setBookmaker(request.getBookmaker());
        if (request.getBookingCode() != null) slip.setBookingCode(request.getBookingCode());
        if (request.getDescription() != null) slip.setDescription(request.getDescription());
        if (request.getTotalOdds()   != null) slip.setTotalOdds(request.getTotalOdds());
        if (request.getType()        != null) slip.setType(request.getType());
        if (request.getValidUntil()  != null) slip.setValidUntil(request.getValidUntil());
        slip.setPublished(request.isPublished());

        if (image != null && !image.isEmpty()) {
            Map<String, Object> uploadResult = cloudinaryService.uploadImage(image, "bettingPlatform/slips");
            slip.setImageUrl((String) uploadResult.get("secure_url"));
        }

        if (request.getGameId() != null) {
            gameRepo.findById(request.getGameId()).ifPresent(slip::setGame);
        }

        slip.setUpdatedAt(LocalDateTime.now(APP_ZONE));
        return mapSlip(bettingSlipRepo.save(slip));
    }

    public BettingSlipResponse updateSlipStatus(UUID slipId, UpdateSlipStatusRequest request) {
        BettingSlip slip = bettingSlipRepo.findById(slipId)
                .orElseThrow(() -> new RuntimeException("Slip not found"));
        slip.setStatus(request.getStatus());
        slip.setUpdatedAt(LocalDateTime.now(APP_ZONE));
        BettingSlip saved = bettingSlipRepo.save(slip);
        log.info("🎫 Slip status: {} → {}", saved.getBookingCode(), saved.getStatus());
        return mapSlip(saved);
    }

    public BettingSlipResponse toggleSlipPublish(UUID slipId) {
        BettingSlip slip = bettingSlipRepo.findById(slipId)
                .orElseThrow(() -> new RuntimeException("Slip not found"));
        slip.setPublished(!slip.isPublished());
        slip.setUpdatedAt(LocalDateTime.now(APP_ZONE));
        return mapSlip(bettingSlipRepo.save(slip));
    }

    public void deleteSlip(UUID slipId) {
        if (!bettingSlipRepo.existsById(slipId))
            throw new RuntimeException("Slip not found");
        bettingSlipRepo.deleteById(slipId);
        log.info("🗑️ Slip deleted: {}", slipId);
    }

    // ═══════════════════════════════════════════════════════════
    // PUBLIC
    // ═══════════════════════════════════════════════════════════

    public List<GameResponse> getPublicTodayGames() {
        return gameRepo.findTodayPublishedGames(startOfToday(), endOfToday())
                .stream().filter(g -> !g.isVipOnly()).limit(3)
                .map(this::mapGamePublic).collect(Collectors.toList());
    }

    public List<GameResponse> getPublicLiveGames() {
        return gameRepo.findByStatusAndPublishedTrue(GameStatus.LIVE)
                .stream().filter(g -> !g.isVipOnly()).limit(2)
                .map(this::mapGamePublic).collect(Collectors.toList());
    }

    public List<BettingSlipResponse> getPublicFreeSlips() {
        return bettingSlipRepo.findByTypeAndPublishedTrue(PredictionType.FREE)
                .stream().limit(2).map(this::mapSlipPublic).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // AUTO-SETTLE
    // ═══════════════════════════════════════════════════════════

    public BettingSlipResponse autoSettleSlip(UUID slipId) {
        BettingSlip slip = bettingSlipRepo.findById(slipId)
                .orElseThrow(() -> new RuntimeException("Slip not found: " + slipId));

        if (slip.getGame() == null)
            throw new RuntimeException("Slip has no linked game — cannot auto-settle");

        Game game = slip.getGame();

        if (game.getHomeScore() == null || game.getAwayScore() == null)
            throw new RuntimeException("Match result not available yet (no score on linked game)");

        if (game.getStatus() != GameStatus.FINISHED)
            throw new RuntimeException("Match is not finished yet — status: " + game.getStatus());

        String raw = (slip.getDescription() != null ? slip.getDescription() : "")
                + " " + slip.getBookingCode();

        List<String> keys = parseOutcomeKeys(raw.toUpperCase());

        if (keys.isEmpty())
            throw new RuntimeException("No prediction outcomes found in slip — cannot auto-settle");

        int h = game.getHomeScore();
        int a = game.getAwayScore();

        Map<String, Boolean> results = Map.of(
                "HOME_WIN",   h > a,
                "AWAY_WIN",   a > h,
                "DRAW",       h == a,
                "BTTS",       h > 0 && a > 0,
                "OVER_1_5",   (h + a) > 1,
                "OVER_2_5",   (h + a) > 2,
                "OVER_3_5",   (h + a) > 3,
                "UNDER_2_5",  (h + a) < 3,
                "UNDER_1_5",  (h + a) < 2,
                "OVER_0_5",   (h + a) > 0
        );

        boolean allCorrect = keys.stream().allMatch(k -> Boolean.TRUE.equals(results.get(k)));
        SlipStatus newStatus = allCorrect ? SlipStatus.WON : SlipStatus.LOST;
        slip.setStatus(newStatus);
        slip.setUpdatedAt(LocalDateTime.now(APP_ZONE));
        BettingSlip saved = bettingSlipRepo.save(slip);

        log.info("🎯 Auto-settled slip {} → {} (score: {}-{}, keys: {})",
                slipId, newStatus, h, a, keys);
        return mapSlip(saved);
    }

    public List<BettingSlipResponse> bulkAutoSettle() {
        List<BettingSlip> pending = bettingSlipRepo.findAll().stream()
                .filter(s -> s.getStatus() == null || s.getStatus() == SlipStatus.ACTIVE)
                .filter(s -> s.getGame() != null)
                .filter(s -> s.getGame().getStatus() == GameStatus.FINISHED)
                .filter(s -> s.getGame().getHomeScore() != null)
                .collect(Collectors.toList());

        List<BettingSlipResponse> settled = new ArrayList<>();
        for (BettingSlip slip : pending) {
            try {
                settled.add(autoSettleSlip(slip.getId()));
            } catch (Exception e) {
                log.warn("⚠️ Could not settle slip {}: {}", slip.getId(), e.getMessage());
            }
        }
        log.info("🎯 Bulk settled {} slips", settled.size());
        return settled;
    }

    private List<String> parseOutcomeKeys(String raw) {
        List<String> keys = new ArrayList<>();
        String[] known = {"HOME_WIN","AWAY_WIN","DRAW","BTTS","OVER_1_5","OVER_2_5",
                "OVER_3_5","UNDER_2_5","UNDER_1_5","OVER_0_5"};
        for (String k : known) {
            if (raw.contains(k)) keys.add(k);
        }
        return keys;
    }

    // ═══════════════════════════════════════════════════════════
    // REGISTERED USER
    // ═══════════════════════════════════════════════════════════

    public List<GameResponse> getRegisteredUserTodayGames() {
        return gameRepo.findTodayPublishedGames(startOfToday(), endOfToday())
                .stream().filter(g -> !g.isVipOnly())
                .map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getRegisteredUserLiveGames() {
        return gameRepo.findByStatusAndPublishedTrue(GameStatus.LIVE)
                .stream().filter(g -> !g.isVipOnly())
                .map(this::mapGame).collect(Collectors.toList());
    }

    public List<BettingSlipResponse> getRegisteredUserFreeSlips() {
        return bettingSlipRepo.findByTypeAndPublishedTrue(PredictionType.FREE)
                .stream().map(this::mapSlip).collect(Collectors.toList());
    }

    public List<GameResponse> getFeaturedGames() {
        return gameRepo.findByFeaturedTrue().stream()
                .filter(g -> !g.isVipOnly()).map(this::mapGame).collect(Collectors.toList());
    }

    public GameResponse getGame(UUID id) {
        return mapGame(gameRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Game not found")));
    }

    // ═══════════════════════════════════════════════════════════
    // FUTURE GAMES
    // ═══════════════════════════════════════════════════════════

    public List<GameResponse> getTomorrowGames() {
        LocalDate tomorrow = LocalDate.now(APP_ZONE).plusDays(1);
        return gameRepo.findPublishedGamesBetween(startOf(tomorrow), endOf(tomorrow))
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getDayAfterTomorrowGames() {
        LocalDate dat = LocalDate.now(APP_ZONE).plusDays(2);
        return gameRepo.findPublishedGamesBetween(startOf(dat), endOf(dat))
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getNextWeekGames() {
        LocalDateTime from = endOfToday().plusSeconds(1);
        LocalDateTime to   = LocalDate.now(APP_ZONE).plusDays(7).atTime(LocalTime.MAX);
        return gameRepo.findPublishedGamesBetween(from, to)
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getGamesByDate(LocalDate date) {
        return gameRepo.findPublishedGamesBetween(startOf(date), endOf(date))
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // VIP USER
    // ═══════════════════════════════════════════════════════════

    public List<GameResponse> getVipTodayGames(UserPrincipal userPrincipal) {
        checkVipAccess(userPrincipal);
        return gameRepo.findTodayPublishedGames(startOfToday(), endOfToday())
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getVipLiveGames(UserPrincipal userPrincipal) {
        checkVipAccess(userPrincipal);
        return gameRepo.findByStatusAndPublishedTrue(GameStatus.LIVE)
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    public List<BettingSlipResponse> getVipSlips(UserPrincipal userPrincipal) {
        checkVipAccess(userPrincipal);
        return bettingSlipRepo.findByTypeAndPublishedTrue(PredictionType.VIP)
                .stream().map(this::mapSlip).collect(Collectors.toList());
    }

    public MatchDetailsResponse getVipMatchDetails(String externalFixtureId, UserPrincipal userPrincipal) {
        checkVipAccess(userPrincipal);
        return getMatchDetails(externalFixtureId);
    }

    public List<BettingSlipResponse> getVipSlipsByBookmaker(String bookmaker, UserPrincipal userPrincipal) {
        checkVipAccess(userPrincipal);
        return bettingSlipRepo.findByBookmakerAndPublishedTrue(bookmaker)
                .stream().map(this::mapSlip).collect(Collectors.toList());
    }

    public List<GameResponse> getUpcomingVipGames(UserPrincipal userPrincipal) {
        checkVipAccess(userPrincipal);
        return gameRepo.findUpcomingVipGames().stream().map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getFeaturedVipGames(UserPrincipal userPrincipal) {
        checkVipAccess(userPrincipal);
        return gameRepo.findByFeaturedTrueAndPublishedTrueAndVipOnlyTrue()
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getVipGamesByLeague(String league, UserPrincipal userPrincipal) {
        checkVipAccess(userPrincipal);
        return gameRepo.findByLeagueAndDate(league, startOfToday(), endOfToday())
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getVipOnlyLiveGames(UserPrincipal userPrincipal) {
        checkVipAccess(userPrincipal);
        return gameRepo.findByStatusAndVipOnlyTrueAndPublishedTrue(GameStatus.LIVE)
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getVipPreviousGames(int days, int page, int size, UserPrincipal userPrincipal) {
        checkVipAccess(userPrincipal);
        int safeDays = Math.min(days, 30);
        int safeSize = Math.min(size, 50);
        LocalDateTime before = LocalDateTime.now(APP_ZONE);
        LocalDateTime cutoff = before.minusDays(safeDays);
        Pageable pageable    = PageRequest.of(page, safeSize);
        return gameRepo.findAllPreviousGames(before, pageable).stream()
                .filter(g -> g.getKickoffTime().isAfter(cutoff))
                .map(this::mapGame).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // PUBLIC EXTRAS
    // ═══════════════════════════════════════════════════════════

    public List<GameResponse> getGamesByLeague(String league) {
        return gameRepo.findByLeagueAndPublishedTrue(league).stream()
                .filter(g -> !g.isVipOnly()).map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getGamesStartingSoon() {
        LocalDateTime now  = LocalDateTime.now(APP_ZONE);
        LocalDateTime soon = now.plusMinutes(15);
        return gameRepo.findGamesStartingSoon(now, soon).stream()
                .filter(g -> !g.isVipOnly()).limit(3)
                .map(this::mapGamePublic).collect(Collectors.toList());
    }

    public List<GameResponse> getUpcomingPublicGames() {
        return gameRepo.findUpcomingPublicGames().stream()
                .map(this::mapGame).collect(Collectors.toList());
    }

    public List<GameResponse> getFeaturedFreeGames() {
        return gameRepo.findByFeaturedTrueAndPublishedTrueAndVipOnlyFalse()
                .stream().map(this::mapGame).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN STATS
    // ═══════════════════════════════════════════════════════════

    public GameStatsResponse getGameStats() {
        return GameStatsResponse.builder()
                .totalGames(gameRepo.count())
                .publishedGames(gameRepo.countByPublishedTrue())
                .vipOnlyGames(gameRepo.countByVipOnlyTrue())
                .upcomingGames(gameRepo.countByStatus(GameStatus.UPCOMING))
                .liveGames(gameRepo.countByStatus(GameStatus.LIVE))
                .finishedGames(gameRepo.countByStatus(GameStatus.FINISHED))
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // HISTORY
    // ═══════════════════════════════════════════════════════════

    public List<GameResponse> getPreviousGames(int days, int page, int size) {
        int safeDays = Math.min(days, 30);
        int safeSize = Math.min(size, 50);
        LocalDateTime before = LocalDateTime.now(APP_ZONE);
        LocalDateTime cutoff = before.minusDays(safeDays);
        Pageable pageable    = PageRequest.of(page, safeSize);
        return gameRepo.findPreviousPublishedGames(before, pageable).stream()
                .filter(g -> !g.isVipOnly())
                .filter(g -> g.getKickoffTime().isAfter(cutoff))
                .map(this::mapGame).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // BOOKING CODE — Auto-create slip from bookmaker share URL
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch a booking code from the bookmaker's API, then auto-create a BettingSlip
     * pre-populated with selections, total odds, and bookmaker name.
     *
     * @param code       the raw booking code e.g. "ABC123"
     * @param bookmaker  "sportybet-gh" | "sportybet-ng" | "betway-gh"
     * @param vipOnly    whether to mark the created slip as VIP
     * @param published  whether to publish immediately
     */
    public BettingSlipResponse createSlipFromBookingCode(
            String code,
            String bookmaker,
            boolean vipOnly,
            boolean published
    ) {
        // 1. Fetch and parse — prints raw JSON + each selection to console
        BookingCodeService.BookingCodeResult result = bookingCodeService.fetch(code, bookmaker);

        System.out.println();
        System.out.println("🔄 Creating BettingSlip from booking code result...");
        System.out.printf ("   Bookmaker  : %s%n", result.bookmaker());
        System.out.printf ("   Code       : %s%n", result.bookingCode());
        System.out.printf ("   Total Odds : %.2f%n", result.totalOdds());
        System.out.printf ("   Selections : %d games%n", result.totalSelections());
        System.out.printf ("   VIP Only   : %s%n", vipOnly);
        System.out.printf ("   Published  : %s%n", published);

        // 2. Build human-readable description from selections
        StringBuilder description = new StringBuilder();
        for (BookingCodeService.SlipSelection sel : result.selections()) {
            description.append(String.format(
                    "%s vs %s (%s) — %s @ %.2f\n",
                    sel.homeTeam(), sel.awayTeam(),
                    sel.league(), sel.outcome(), sel.odds()
            ));
        }
        System.out.println();
        System.out.println("📝 Generated description:");
        System.out.println(description);

        // 3. Build and save BettingSlip
        BettingSlip slip = BettingSlip.builder()
                .bookmaker(result.bookmaker())
                .bookingCode(result.bookingCode())
                .totalOdds(result.totalOdds())
                .description(description.toString().trim())
                .type(vipOnly ? PredictionType.VIP : PredictionType.FREE)
                .published(published)
                .createdAt(LocalDateTime.now(APP_ZONE))
                .updatedAt(LocalDateTime.now(APP_ZONE))
                .build();

        BettingSlip saved = bettingSlipRepo.save(slip);

        System.out.printf("✅ BettingSlip saved! ID: %s%n", saved.getId());
        log.info("🎫 Slip auto-created from code {} [{}]: id={}, odds={}, selections={}",
                code, bookmaker, saved.getId(), result.totalOdds(), result.totalSelections());

        return mapSlip(saved);
    }

    // ═══════════════════════════════════════════════════════════
    // VIP CHECK
    // ═══════════════════════════════════════════════════════════

    private void checkVipAccess(UserPrincipal userPrincipal) {
        User user = userRepo.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        vipSubscriptionRepo.findByUserAndActiveTrue(user)
                .orElseThrow(() -> new RuntimeException(
                        "👑 VIP subscription required. Upgrade to access this content."));
    }

    // ═══════════════════════════════════════════════════════════
    // MAPPERS
    // ═══════════════════════════════════════════════════════════

    public GameResponse mapGame(Game g) {
        return GameResponse.builder()
                .id(g.getId())
                .externalFixtureId(g.getExternalFixtureId())
                .homeTeam(g.getHomeTeam())
                .awayTeam(g.getAwayTeam())
                .league(g.getLeague())
                .country(g.getCountry())
                .leagueLogo(g.getLeagueLogo())
                .homeLogo(g.getHomeLogo())
                .awayLogo(g.getAwayLogo())
                .kickoffTime(g.getKickoffTime())
                .homeScore(g.getHomeScore())
                .awayScore(g.getAwayScore())
                .elapsedMinutes(g.getElapsedMinutes())
                .matchPeriod(g.getMatchPeriod())
                .status(g.getStatus())
                .published(g.isPublished())
                .featured(g.isFeatured())
                .vipOnly(g.isVipOnly())
                .homeWinOdds(g.getHomeWinOdds())
                .drawOdds(g.getDrawOdds())
                .awayWinOdds(g.getAwayWinOdds())
                .over25Odds(g.getOver25Odds())
                .under25Odds(g.getUnder25Odds())
                .over15Odds(g.getOver15Odds())
                .over35Odds(g.getOver35Odds())
                .oddsBookmaker(g.getOddsBookmaker())
                .build();
    }

    private GameResponse mapGamePublic(Game g) {
        return GameResponse.builder()
                .id(g.getId())
                .homeTeam(g.getHomeTeam())
                .awayTeam(g.getAwayTeam())
                .league(g.getLeague())
                .leagueLogo(g.getLeagueLogo())
                .homeLogo(g.getHomeLogo())
                .awayLogo(g.getAwayLogo())
                .kickoffTime(g.getKickoffTime())
                .status(g.getStatus())
                .build();
    }

    private BettingSlipResponse mapSlip(BettingSlip s) {
        BettingSlipResponse.BettingSlipResponseBuilder builder = BettingSlipResponse.builder()
                .id(s.getId())
                .bookmaker(s.getBookmaker())
                .bookingCode(s.getBookingCode())
                .description(s.getDescription())
                .totalOdds(s.getTotalOdds())
                .imageUrl(s.getImageUrl())
                .type(s.getType())
                .status(s.getStatus())
                .published(s.isPublished())
                .validUntil(s.getValidUntil())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt());

        if (s.getGame() != null) {
            Game g = s.getGame();
            builder.gameId(g.getId())
                    .homeTeam(g.getHomeTeam())
                    .awayTeam(g.getAwayTeam())
                    .league(g.getLeague())
                    .homeLogo(g.getHomeLogo())
                    .awayLogo(g.getAwayLogo())
                    .kickoffTime(g.getKickoffTime())
                    .gameStatus(g.getStatus() != null ? g.getStatus().name() : null)
                    .homeScore(g.getHomeScore())
                    .awayScore(g.getAwayScore())
                    .homeWinOdds(g.getHomeWinOdds())
                    .drawOdds(g.getDrawOdds())
                    .awayWinOdds(g.getAwayWinOdds())
                    .over15Odds(g.getOver15Odds())
                    .over25Odds(g.getOver25Odds())
                    .over35Odds(g.getOver35Odds())
                    .under25Odds(g.getUnder25Odds());
        }

        return builder.build();
    }

    private BettingSlipResponse mapSlipPublic(BettingSlip s) {
        return BettingSlipResponse.builder()
                .id(s.getId())
                .bookmaker(s.getBookmaker())
                .bookingCode("****")
                .description(s.getDescription())
                .totalOdds(s.getTotalOdds())
                .type(s.getType())
                .status(s.getStatus())
                .build();
    }
}