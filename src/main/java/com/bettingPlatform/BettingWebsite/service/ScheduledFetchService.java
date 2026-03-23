package com.bettingPlatform.BettingWebsite.service;

import com.bettingPlatform.BettingWebsite.entity.*;
import com.bettingPlatform.BettingWebsite.entity.repos.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledFetchService {

    private final ApiFootballClient     apiFootballClient;
    private final OddsApiClient         oddsApiClient;
    private final GameRepo              gameRepo;
    private final PredictionRepo        predictionRepo;
    private final VipSubscriptionRepo   vipSubscriptionRepo;
    private final SimpMessagingTemplate messagingTemplate;

    // Max stale games to hit individually per scheduler tick
    // 10 req/min free tier → safe at 8 per tick with 30s interval
    private static final int MAX_STALE_PER_TICK = 8;

    // ══════════════════════════════════════════════════════════════
    // 1. FETCH TODAY'S FIXTURES — every 30 min
    // ══════════════════════════════════════════════════════════════
    @Scheduled(fixedRate = 1800000)
    public void fetchTodayFixtures() {
        String dateFrom = LocalDate.now().minusDays(1).toString();
        String dateTo   = LocalDate.now().plusDays(6).toString();
        log.info("🔄 [FIXTURES] Fetching {} → {}", dateFrom, dateTo);
        try {
            JsonNode response = apiFootballClient.getMatchesByDateRange(dateFrom, dateTo);
            if (response == null || !response.has("matches")) {
                log.warn("⚠️ No 'matches' node in fixture response");
                return;
            }
            JsonNode matches = response.get("matches");
            log.info("📦 {} fixtures returned", matches.size());
            for (JsonNode match : matches) {
                saveOrUpdateGame(match);
            }
            pushTodayGamesUpdate();
            log.info("✅ Fixtures done, pushed to WebSocket");
        } catch (Exception e) {
            log.error("❌ fetchTodayFixtures failed: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2. FETCH LIVE SCORES — every 30 seconds
    // ══════════════════════════════════════════════════════════════
    @Scheduled(fixedRate = 30000)
    public void fetchLiveScores() {
        try {
            List<Map<String, Object>> liveUpdates = new ArrayList<>();

            // ── 2a. Currently live games from live endpoint ────────
            JsonNode response = apiFootballClient.getLiveFixtures();
            if (response != null && response.has("matches")) {
                JsonNode matches = response.get("matches");
                if (!matches.isEmpty()) {
                    log.info("🔴 [LIVE] {} matches in progress", matches.size());
                    for (JsonNode match : matches) {
                        String rawId      = match.path("id").asText();
                        String externalId = rawId.startsWith("af-") ? rawId : "af-" + rawId;
                        int    homeScore  = match.path("score").path("fullTime").path("home").asInt();
                        int    awayScore  = match.path("score").path("fullTime").path("away").asInt();
                        String status     = match.path("status").asText();
                        int    elapsed    = match.path("minute").asInt();
                        String period     = resolveMatchPeriod(status, elapsed);

                        Optional<Game> gameOpt = gameRepo.findByExternalFixtureId(externalId);
                        if (gameOpt.isEmpty()) gameOpt = gameRepo.findByExternalFixtureId(rawId);

                        gameOpt.ifPresent(game -> {
                            game.setHomeScore(homeScore);
                            game.setAwayScore(awayScore);
                            game.setStatus(mapStatus(status));
                            game.setElapsedMinutes(elapsed);
                            game.setMatchPeriod(period);
                            game.setUpdatedAt(LocalDateTime.now());
                            gameRepo.save(game);
                        });

                        Map<String, Object> u = new HashMap<>();
                        u.put("fixtureId",   externalId);
                        u.put("homeScore",   homeScore);
                        u.put("awayScore",   awayScore);
                        u.put("status",      status);
                        u.put("elapsed",     elapsed);
                        u.put("matchPeriod", period);
                        u.put("homeTeam",    match.path("homeTeam").path("name").asText());
                        u.put("awayTeam",    match.path("awayTeam").path("name").asText());
                        u.put("league",      match.path("competition").path("name").asText());
                        liveUpdates.add(u);
                    }
                }
            }

            // ── 2b. Stale games — capped at MAX_STALE_PER_TICK ────
            fetchFinishedGameScores(liveUpdates);

            // ── 2c. Push to WebSocket ──────────────────────────────
            if (!liveUpdates.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/live-scores", (Object) liveUpdates);
                log.debug("📡 Pushed {} updates to WebSocket", liveUpdates.size());
            }

        } catch (Exception e) {
            log.error("❌ fetchLiveScores failed: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 2b. FETCH FINISHED GAME SCORES — capped per tick
    //
    // FIX: When the API returns null/empty we no longer blindly mark
    // the game FINISHED (which previously saved it without scores).
    // Instead we just skip it and let the next tick retry.
    //
    // FIX: api-sports wraps its response in:
    //   { "response": [ { "fixture": {...}, "goals": { "home": N, "away": N }, ... } ] }
    // We now navigate that structure correctly to read goals.home / goals.away.
    // ══════════════════════════════════════════════════════════════
    private void fetchFinishedGameScores(List<Map<String, Object>> liveUpdates) {
        LocalDateTime cutoff     = LocalDateTime.now().minusMinutes(95);
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        List<Game> staleGames = gameRepo.findStaleGames(cutoff, startOfDay);
        if (staleGames.isEmpty()) return;

        log.info("🔍 {} stale games — processing up to {} this tick",
                staleGames.size(), MAX_STALE_PER_TICK);

        List<Game> batch = staleGames.stream()
                .limit(MAX_STALE_PER_TICK)
                .toList();

        for (Game game : batch) {
            try {
                String extId = game.getExternalFixtureId();
                if (extId == null || extId.isBlank()) continue;

                // Strip the "af-" prefix to get the numeric api-sports fixture ID
                String numericStr = extId.replaceAll("^[a-zA-Z]+-", "");
                int fixtureId;
                try {
                    fixtureId = Integer.parseInt(numericStr);
                } catch (NumberFormatException e) {
                    // Non-numeric ID (e.g. football-data.org fixture) — skip silently
                    log.warn("⚠️ Non-numeric fixture ID '{}' — skipping", extId);
                    continue;
                }

                // ── Call api-sports ───────────────────────────────
                JsonNode apiResponse = apiFootballClient.getFixtureById(fixtureId);

                // api-sports wraps data in: { "response": [ { "fixture":{}, "goals":{} } ] }
                // BUG WAS HERE: old code did fixture.path("id") which is always missing
                // because the top-level node is the wrapper, not the fixture itself.
                if (apiResponse == null
                        || !apiResponse.has("response")
                        || apiResponse.path("response").isEmpty()) {
                    // API returned nothing useful — skip and retry next tick
                    // DO NOT mark FINISHED here; that was the original bug.
                    log.warn("⚠️ Empty api-sports response for fixture {} — will retry next tick", extId);
                    continue;
                }

                JsonNode fixtureNode = apiResponse.path("response").get(0);
                JsonNode statusNode  = fixtureNode.path("fixture").path("status");
                JsonNode goalsNode   = fixtureNode.path("goals");
                JsonNode elapsedNode = fixtureNode.path("fixture").path("status").path("elapsed");

                String apiStatus = statusNode.path("short").asText(); // "FT", "1H", "2H", "HT", etc.
                int    elapsed   = elapsedNode.asInt(0);

                // goals.home / goals.away are null for unfinished/no-score games
                // asInt(-1) lets us distinguish "0" (real zero) from "null" (not set)
                int homeScore = goalsNode.path("home").asInt(-1);
                int awayScore = goalsNode.path("away").asInt(-1);

                // Map api-sports short status codes to our GameStatus
                GameStatus newStatus = mapApiSportsStatus(apiStatus);
                String     period    = resolveApiSportsPeriod(apiStatus, elapsed);

                // Only write scores if the API actually gave us values
                if (homeScore >= 0) game.setHomeScore(homeScore);
                if (awayScore >= 0) game.setAwayScore(awayScore);
                game.setStatus(newStatus);
                game.setElapsedMinutes(elapsed);
                game.setMatchPeriod(period);
                game.setUpdatedAt(LocalDateTime.now());
                gameRepo.save(game);

                log.info("✅ {} vs {} → {}-{} [{}]",
                        game.getHomeTeam(), game.getAwayTeam(),
                        homeScore >= 0 ? homeScore : "?",
                        awayScore >= 0 ? awayScore : "?",
                        newStatus);

                Map<String, Object> update = new HashMap<>();
                update.put("fixtureId",   extId);
                update.put("homeScore",   homeScore >= 0 ? homeScore : game.getHomeScore());
                update.put("awayScore",   awayScore >= 0 ? awayScore : game.getAwayScore());
                update.put("status",      apiStatus);
                update.put("elapsed",     elapsed);
                update.put("matchPeriod", period);
                update.put("homeTeam",    game.getHomeTeam());
                update.put("awayTeam",    game.getAwayTeam());
                update.put("league",      game.getLeague());
                liveUpdates.add(update);

                // 6s gap = stay under 10 req/min free tier limit
                Thread.sleep(6000);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("⚠️ Score update failed for {}: {}", game.getId(), e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 3. FETCH ODDS — every 3 hours
    // ══════════════════════════════════════════════════════════════
    @Scheduled(fixedRate = 10800000)
    public void fetchOdds() {
        log.info("🔄 [ODDS] Fetching...");
        List<String> soccerKeys = List.of(
                "soccer_epl", "soccer_spain_la_liga", "soccer_italy_serie_a",
                "soccer_germany_bundesliga", "soccer_france_ligue_one",
                "soccer_uefa_champs_league", "soccer_uefa_europa_league"
        );
        for (String sportKey : soccerKeys) {
            try {
                JsonNode response = oddsApiClient.getFullOddsForSport(sportKey, "uk");
                if (response == null || !response.isArray()) continue;
                for (JsonNode event : response) saveOrUpdateOdds(event, sportKey);
                log.info("✅ Odds saved: {}", sportKey);
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("❌ Odds failed for {}: {}", sportKey, e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 4. AUTO-SETTLE PREDICTIONS — every 5 min
    // ══════════════════════════════════════════════════════════════
    @Scheduled(fixedRate = 300000)
    public void autoSettlePredictions() {
        List<Prediction> pending = predictionRepo
                .findByStatusAndMatchDateLessThanEqual(PredictionStatus.PENDING, LocalDate.now());
        if (pending.isEmpty()) return;
        log.info("🔄 [SETTLE] {} predictions to settle", pending.size());
        for (Prediction prediction : pending) {
            try {
                if (prediction.getExternalFixtureId() == null) continue;
                String numericId = prediction.getExternalFixtureId().replaceAll("^[a-zA-Z]+-", "");
                JsonNode apiResponse = apiFootballClient.getFixtureById(Integer.parseInt(numericId));
                if (apiResponse == null
                        || !apiResponse.has("response")
                        || apiResponse.path("response").isEmpty()) continue;

                JsonNode fixtureNode = apiResponse.path("response").get(0);
                String   shortStatus = fixtureNode.path("fixture").path("status").path("short").asText();
                if (!shortStatus.equals("FT") && !shortStatus.equals("AET") && !shortStatus.equals("PEN")) continue;

                int home  = fixtureNode.path("goals").path("home").asInt();
                int away  = fixtureNode.path("goals").path("away").asInt();
                boolean won = evaluateTip(prediction.getTip(), home, away, home + away);
                prediction.setStatus(won ? PredictionStatus.WON : PredictionStatus.LOST);
                prediction.setResult(won ? "✅" : "❌");
                prediction.setUpdatedAt(LocalDateTime.now());
                predictionRepo.save(prediction);
                log.info("✅ Settled: {} {} — {}", prediction.getHomeTeam(),
                        prediction.getAwayTeam(), prediction.getResult());
            } catch (Exception e) {
                log.error("❌ Settle failed {}: {}", prediction.getId(), e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 5. EXPIRE VIP SUBSCRIPTIONS — every hour
    // ══════════════════════════════════════════════════════════════
    @Scheduled(fixedRate = 3600000)
    public void expireVipSubscriptions() {
        List<VipSubscription> expired = vipSubscriptionRepo
                .findExpiredButStillActive(LocalDateTime.now());
        if (expired.isEmpty()) return;
        expired.forEach(sub -> {
            sub.setActive(false);
            vipSubscriptionRepo.save(sub);
            log.info("⌛ VIP expired: {}", sub.getUser().getEmail());
        });
        log.info("✅ Expired {} VIP subscriptions", expired.size());
    }

    // ══════════════════════════════════════════════════════════════
    // PUSH TODAY'S GAMES TO WEBSOCKET
    // ══════════════════════════════════════════════════════════════
    private void pushTodayGamesUpdate() {
        try {
            LocalDateTime start   = LocalDate.now().atStartOfDay();
            LocalDateTime end     = start.plusDays(1).minusSeconds(1);
            List<Game> todayGames = gameRepo.findAllTodayGames(start, end);

            List<Map<String, Object>> payload = todayGames.stream().map(g -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id",                g.getId());
                m.put("homeTeam",          g.getHomeTeam());
                m.put("awayTeam",          g.getAwayTeam());
                m.put("homeScore",         g.getHomeScore());
                m.put("awayScore",         g.getAwayScore());
                m.put("status",            g.getStatus());
                m.put("matchPeriod",       g.getMatchPeriod());
                m.put("elapsed",           g.getElapsedMinutes());
                m.put("league",            g.getLeague());
                m.put("kickoffTime",       g.getKickoffTime());
                m.put("externalFixtureId", g.getExternalFixtureId());
                return m;
            }).toList();

            messagingTemplate.convertAndSend("/topic/games-update", (Object) payload);
            log.debug("📡 Pushed {} today's games to WebSocket", payload.size());
        } catch (Exception e) {
            log.warn("⚠️ pushTodayGamesUpdate failed: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SAVE OR UPDATE GAME (football-data.org source)
    // ══════════════════════════════════════════════════════════════
    private void saveOrUpdateGame(JsonNode match) {
        String externalId = match.path("id").asText();
        if (externalId.isBlank()) return;

        Game    game  = gameRepo.findByExternalFixtureId(externalId).orElse(null);
        boolean isNew = game == null;
        if (isNew) game = new Game();

        game.setExternalFixtureId(externalId);
        game.setHomeTeam(match.path("homeTeam").path("name").asText());
        game.setAwayTeam(match.path("awayTeam").path("name").asText());
        game.setHomeLogo(match.path("homeTeam").path("crest").asText());
        game.setAwayLogo(match.path("awayTeam").path("crest").asText());
        game.setLeague(match.path("competition").path("name").asText());
        game.setLeagueLogo(match.path("competition").path("emblem").asText());
        game.setCountry(match.path("area").path("name").asText());

        String kickoff = match.path("utcDate").asText();
        if (!kickoff.isBlank()) {
            game.setKickoffTime(LocalDateTime.parse(
                    kickoff.replace("Z", "").substring(0, 19)));
        }

        String status  = match.path("status").asText();
        int    elapsed = match.path("minute").asInt(0);
        game.setStatus(mapStatus(status));
        game.setMatchPeriod(resolveMatchPeriod(status, elapsed));
        game.setElapsedMinutes(elapsed);

        JsonNode fullTime = match.path("score").path("fullTime");
        if (!fullTime.path("home").isNull() && !fullTime.path("home").isMissingNode()) {
            game.setHomeScore(fullTime.path("home").asInt());
            game.setAwayScore(fullTime.path("away").asInt());
        }

        if (isNew) {
            game.setPublished(true);
            game.setVipOnly(false);
            game.setFeatured(false);
            game.setCreatedAt(LocalDateTime.now());
            log.info("🆕 New: {} vs {} [{}]", game.getHomeTeam(), game.getAwayTeam(), externalId);
        }

        game.setUpdatedAt(LocalDateTime.now());
        gameRepo.save(game);
    }

    // ══════════════════════════════════════════════════════════════
    // SAVE OR UPDATE ODDS
    // ══════════════════════════════════════════════════════════════
    private void saveOrUpdateOdds(JsonNode event, String sportKey) {
        String homeTeam = event.path("home_team").asText();
        String awayTeam = event.path("away_team").asText();
        Optional<Game> gameOpt = gameRepo.findByHomeTeamContainingAndAwayTeamContaining(
                normalizeTeamName(homeTeam), normalizeTeamName(awayTeam));
        if (gameOpt.isEmpty()) return;
        Game game = gameOpt.get();
        for (JsonNode bookmaker : event.path("bookmakers")) {
            for (JsonNode market : bookmaker.path("markets")) {
                String key = market.path("key").asText();
                if (key.equals("h2h")) {
                    for (JsonNode o : market.path("outcomes")) {
                        String name  = o.path("name").asText();
                        double price = o.path("price").asDouble();
                        if      (name.equalsIgnoreCase(homeTeam)) game.setHomeWinOdds(price);
                        else if (name.equalsIgnoreCase(awayTeam)) game.setAwayWinOdds(price);
                        else    game.setDrawOdds(price);
                    }
                }
                if (key.equals("totals")) {
                    for (JsonNode o : market.path("outcomes")) {
                        String name  = o.path("name").asText();
                        double point = o.path("point").asDouble();
                        double price = o.path("price").asDouble();
                        if (point == 2.5) {
                            if (name.equals("Over"))  game.setOver25Odds(price);
                            if (name.equals("Under")) game.setUnder25Odds(price);
                        }
                        if (point == 1.5 && name.equals("Over")) game.setOver15Odds(price);
                        if (point == 3.5 && name.equals("Over")) game.setOver35Odds(price);
                    }
                }
            }
            game.setOddsBookmaker(bookmaker.path("title").asText());
            break;
        }
        game.setUpdatedAt(LocalDateTime.now());
        gameRepo.save(game);
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Maps api-sports SHORT status codes to our GameStatus enum.
     * api-sports uses: TBD, NS, 1H, HT, 2H, ET, BT, P, SUSP, INT, FT, AET, PEN, PST, CANC, ABD, AWD, WO, LIVE
     */
    private GameStatus mapApiSportsStatus(String shortStatus) {
        return switch (shortStatus) {
            case "TBD", "NS"                    -> GameStatus.UPCOMING;
            case "1H", "HT", "2H", "ET",
                 "BT", "P", "INT", "LIVE"       -> GameStatus.LIVE;
            case "FT", "AET", "PEN"             -> GameStatus.FINISHED;
            case "SUSP", "ABD"                  -> GameStatus.CANCELLED;
            case "PST"                          -> GameStatus.POSTPONED;
            case "CANC", "WO", "AWD"            -> GameStatus.CANCELLED;
            default                             -> GameStatus.UPCOMING;
        };
    }

    /**
     * Resolves a human-readable match period from api-sports short status codes.
     */
    private String resolveApiSportsPeriod(String shortStatus, int elapsed) {
        return switch (shortStatus) {
            case "1H"           -> "1st Half";
            case "HT"           -> "Half Time";
            case "2H"           -> "2nd Half";
            case "ET"           -> "Extra Time";
            case "BT"           -> "Break Time";
            case "P"            -> "Penalty";
            case "FT"           -> "Full Time";
            case "AET"          -> "After Extra Time";
            case "PEN"          -> "Penalties";
            case "PST"          -> "Postponed";
            case "SUSP", "INT"  -> "Suspended";
            case "CANC", "ABD",
                 "WO",  "AWD"   -> "Cancelled";
            case "NS", "TBD"    -> "Not Started";
            default             -> elapsed > 0 ? elapsed + "'" : "Not Started";
        };
    }

    /**
     * Maps football-data.org status strings (used by saveOrUpdateGame / fetchTodayFixtures).
     */
    private String resolveMatchPeriod(String status, int elapsed) {
        return switch (status) {
            case "IN_PLAY"                    -> elapsed <= 45 ? "1st Half" : "2nd Half";
            case "PAUSED"                     -> "Half Time";
            case "FINISHED"                   -> "Full Time";
            case "SCHEDULED", "TIMED"         -> "Not Started";
            case "POSTPONED"                  -> "Postponed";
            case "CANCELLED", "SUSPENDED"     -> "Cancelled";
            default -> elapsed > 0 ? elapsed + "'" : "Not Started";
        };
    }

    private GameStatus mapStatus(String status) {
        return switch (status) {
            case "SCHEDULED", "TIMED"         -> GameStatus.UPCOMING;
            case "IN_PLAY", "PAUSED"          -> GameStatus.LIVE;
            case "FINISHED"                   -> GameStatus.FINISHED;
            case "CANCELLED", "SUSPENDED"     -> GameStatus.CANCELLED;
            case "POSTPONED"                  -> GameStatus.POSTPONED;
            default                           -> GameStatus.UPCOMING;
        };
    }

    private String normalizeTeamName(String name) {
        return name.replace(" FC","").replace("FC ","")
                .replace(" CF","").replace("CF ","")
                .replace(" AC","").replace("AC ","")
                .replace(" AFC","").replace("AFC ","")
                .replace(" SC","").replace("SC ","")
                .replace("SSC ","").replace("AS ","")
                .replace("RCD ","").replace("RC ","")
                .replace(" United","").replace(" City","").trim();
    }

    private boolean evaluateTip(String tip, int home, int away, int total) {
        if (tip == null) return false;
        return switch (tip.toLowerCase().trim()) {
            case "home win","1x2 - home win","1up - home win"    -> home > away;
            case "away win","1x2 - away win","1up - away win"    -> away > home;
            case "draw"                                           -> home == away;
            case "btts - yes","both teams to score"              -> home > 0 && away > 0;
            case "btts - no"                                     -> home == 0 || away == 0;
            case "over 0.5 goals"                                -> total > 0;
            case "over 1.5 goals"                                -> total > 1;
            case "over 2.5 goals"                                -> total > 2;
            case "over 3.5 goals"                                -> total > 3;
            case "over 4.5 goals"                                -> total > 4;
            case "under 1.5 goals"                               -> total < 2;
            case "under 2.5 goals"                               -> total < 3;
            case "under 3.5 goals"                               -> total < 4;
            case "double chance - home or draw"                  -> home >= away;
            case "double chance - away or draw"                  -> away >= home;
            default -> { log.warn("⚠️ Unknown tip: '{}'", tip); yield false; }
        };
    }
}