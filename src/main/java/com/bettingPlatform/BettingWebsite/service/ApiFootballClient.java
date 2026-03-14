package com.bettingPlatform.BettingWebsite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.function.Function;

@Component
@Slf4j
public class ApiFootballClient {

    // ── football-data.org ─────────────────────────────────────────
    @Value("${api.football.key}")
    private String fdApiKey;

    @Value("${api.football.base-url}")
    private String fdBaseUrl;

    // ── API-Football (api-sports.io) ──────────────────────────────
    @Value("${api.apifootball.key:#{null}}")
    private String afApiKey;

    @Value("${api.apifootball.base-url:https://v3.football.api-sports.io}")
    private String afBaseUrl;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public ApiFootballClient(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }


    // ═══════════════════════════════════════════════════════════
    // PUBLIC API — used by ScheduledFetchService
    // ═══════════════════════════════════════════════════════════

    public JsonNode getMatchesByDateRange(String dateFrom, String dateTo) {
        log.info("📅 Fetching matches — from: {}, to: {}", dateFrom, dateTo);

        ArrayNode merged = objectMapper.createArrayNode();

        // ── Source 1: football-data.org ───────────────────────
        try {
            JsonNode fdResponse = executeFd(c -> c.get()
                    .uri(u -> u.path("/matches")
                            .queryParam("dateFrom", dateFrom)
                            .queryParam("dateTo", dateTo)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block());

            if (fdResponse.has("matches")) {
                fdResponse.get("matches").forEach(merged::add);
                log.info("✅ [FD] {} matches fetched", fdResponse.get("matches").size());
            }
        } catch (Exception e) {
            log.error("❌ [FD] Failed to fetch matches: {}", e.getMessage());
        }

        // ── Source 2: API-Football ────────────────────────────
        if (isApiFootballEnabled()) {
            try {
                LocalDate from = LocalDate.parse(dateFrom);
                LocalDate to   = LocalDate.parse(dateTo);

                for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                    final String dateStr = date.toString();
                    JsonNode afResponse = executeAf(c -> c.get()
                            .uri(u -> u.path("/fixtures")
                                    .queryParam("date", dateStr)
                                    .build())
                            .retrieve()
                            .bodyToMono(String.class)
                            .block());

                    if (afResponse.has("response")) {
                        int count = 0;
                        for (JsonNode fixture : afResponse.get("response")) {
                            merged.add(normaliseAfFixture(fixture));
                            count++;
                        }
                        log.info("✅ [AF] {} matches fetched for {}", count, dateStr);
                    }

                    Thread.sleep(200);
                }
            } catch (Exception e) {
                log.error("❌ [AF] Failed to fetch matches: {}", e.getMessage());
            }
        }

        ArrayNode deduped = deduplicateMatches(merged);
        log.info("📦 Total after merge + dedup: {}", deduped.size());

        var wrapper = objectMapper.createObjectNode();
        wrapper.set("matches", deduped);
        return wrapper;
    }

    public JsonNode getLiveFixtures() {
        log.info("🔴 Fetching all live fixtures");

        ArrayNode merged = objectMapper.createArrayNode();

        try {
            JsonNode fdResponse = executeFd(c -> c.get()
                    .uri(u -> u.path("/matches")
                            .queryParam("status", "IN_PLAY,PAUSED")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block());

            if (fdResponse.has("matches")) {
                fdResponse.get("matches").forEach(merged::add);
            }
        } catch (Exception e) {
            log.error("❌ [FD] Live fetch failed: {}", e.getMessage());
        }

        if (isApiFootballEnabled()) {
            try {
                JsonNode afResponse = executeAf(c -> c.get()
                        .uri(u -> u.path("/fixtures")
                                .queryParam("live", "all")
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block());

                if (afResponse.has("response")) {
                    afResponse.get("response")
                            .forEach(f -> merged.add(normaliseAfFixture(f)));
                }
            } catch (Exception e) {
                log.error("❌ [AF] Live fetch failed: {}", e.getMessage());
            }
        }

        ArrayNode deduped = deduplicateMatches(merged);
        var wrapper = objectMapper.createObjectNode();
        wrapper.set("matches", deduped);
        return wrapper;
    }

    /**
     * FIX: This method was calling executeFd (football-data.org) instead of executeAf (api-sports).
     * All "af-" prefixed fixture IDs are api-sports IDs and must be fetched from api-sports.
     * The response shape is: { "response": [ { "fixture":{}, "goals":{ "home": N, "away": N } } ] }
     * ScheduledFetchService.fetchFinishedGameScores() reads response[0].goals.home/away.
     */
    public JsonNode getFixtureById(int fixtureId) {
        log.info("🔍 [AF] Fetching fixture by ID: {}", fixtureId);
        return executeAf(c -> c.get()                  // ✅ FIX: was executeFd, must be executeAf
                .uri(u -> u.path("/fixtures")
                        .queryParam("id", fixtureId)   // ✅ FIX: api-sports uses /fixtures?id=N, not /matches/{id}
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getTodayMatches() {
        String today = LocalDate.now().toString();
        return getMatchesByDateRange(today, today);
    }

    public JsonNode getMatchesByDateRange(String dateFrom, String dateTo, int leagueId, int season) {
        return getMatchesByDateRange(dateFrom, dateTo);
    }


    // ═══════════════════════════════════════════════════════════
    // NORMALISER — converts API-Football shape → football-data.org shape
    // ═══════════════════════════════════════════════════════════

    private JsonNode normaliseAfFixture(JsonNode af) {
        var out = objectMapper.createObjectNode();

        // ID — prefix with "af-" to avoid collisions
        String fixtureId = af.path("fixture").path("id").asText();
        out.put("id", "af-" + fixtureId);

        // Date
        String date = af.path("fixture").path("date").asText();
        if (date.length() >= 19) {
            out.put("utcDate", date.substring(0, 19));
        }

        // Status
        String shortStatus = af.path("fixture").path("status").path("short").asText();
        out.put("status", mapAfStatus(shortStatus));
        out.put("minute", af.path("fixture").path("status").path("elapsed").asInt(0));

        // Home team
        var homeTeam = objectMapper.createObjectNode();
        homeTeam.put("id",    af.path("teams").path("home").path("id").asInt(0));
        homeTeam.put("name",  af.path("teams").path("home").path("name").asText());
        homeTeam.put("crest", af.path("teams").path("home").path("logo").asText());
        out.set("homeTeam", homeTeam);

        // Away team
        var awayTeam = objectMapper.createObjectNode();
        awayTeam.put("id",    af.path("teams").path("away").path("id").asInt(0));
        awayTeam.put("name",  af.path("teams").path("away").path("name").asText());
        awayTeam.put("crest", af.path("teams").path("away").path("logo").asText());
        out.set("awayTeam", awayTeam);

        // Competition
        var competition = objectMapper.createObjectNode();
        competition.put("id",     af.path("league").path("id").asInt(0));
        competition.put("name",   af.path("league").path("name").asText());
        competition.put("emblem", af.path("league").path("logo").asText());
        out.set("competition", competition);

        // Area/country
        var area = objectMapper.createObjectNode();
        area.put("name", af.path("league").path("country").asText());
        out.set("area", area);

        // Season
        var season = objectMapper.createObjectNode();
        String seasonYear = af.path("league").path("season").asText("");
        season.put("startDate", seasonYear.isBlank() ? "" : seasonYear + "-01-01");
        out.set("season", season);

        // Score
        var score    = objectMapper.createObjectNode();
        var fullTime = objectMapper.createObjectNode();
        JsonNode goals = af.path("goals");
        if (!goals.path("home").isNull() && !goals.path("home").isMissingNode()) {
            fullTime.put("home", goals.path("home").asInt());
            fullTime.put("away", goals.path("away").asInt());
        } else {
            fullTime.putNull("home");
            fullTime.putNull("away");
        }
        score.set("fullTime", fullTime);
        out.set("score", score);

        log.debug("🔍 [ID CHECK] af-{} homeId={} awayId={} leagueId={}",
                fixtureId,
                af.path("teams").path("home").path("id").asInt(0),
                af.path("teams").path("away").path("id").asInt(0),
                af.path("league").path("id").asInt(0));

        return out;
    }

    private String mapAfStatus(String shortStatus) {
        return switch (shortStatus) {
            case "NS", "TBD"           -> "SCHEDULED";
            case "1H", "2H", "ET", "P" -> "IN_PLAY";
            case "HT"                  -> "PAUSED";
            case "FT", "AET", "PEN"    -> "FINISHED";
            case "SUSP", "INT"         -> "SUSPENDED";
            case "PST"                 -> "POSTPONED";
            case "CANC", "ABD"         -> "CANCELLED";
            default                    -> "SCHEDULED";
        };
    }


    // ═══════════════════════════════════════════════════════════
    // DE-DUPLICATION
    // ═══════════════════════════════════════════════════════════

    private ArrayNode deduplicateMatches(ArrayNode matches) {
        ArrayNode result = objectMapper.createArrayNode();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();

        for (JsonNode match : matches) {
            String home = normaliseForDedup(match.path("homeTeam").path("name").asText());
            String away = normaliseForDedup(match.path("awayTeam").path("name").asText());
            String date = match.path("utcDate").asText();
            if (date.length() >= 10) date = date.substring(0, 10);

            String key = home + "|" + away + "|" + date;

            if (seen.add(key)) {
                result.add(match);
            } else {
                log.debug("🔁 Duplicate skipped: {} vs {} on {}", home, away, date);
            }
        }

        return result;
    }

    private String normaliseForDedup(String name) {
        return name.toLowerCase()
                .replace(" fc", "").replace("fc ", "")
                .replace(" afc", "").replace("afc ", "")
                .replace(" cf", "").replace(" sc", "")
                .replace(" united", "").replace(" city", "")
                .replace("manchester ", "man ")
                .replace("and hove albion", "")
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }


    // ═══════════════════════════════════════════════════════════
    // HTTP CLIENTS — .clone() prevents shared builder mutation
    // ═══════════════════════════════════════════════════════════

    private WebClient fdClient() {
        return webClientBuilder.clone()
                .baseUrl(fdBaseUrl)
                .defaultHeader("X-Auth-Token", fdApiKey)
                .build();
    }

    private WebClient afClient() {
        return webClientBuilder.clone()
                .baseUrl(afBaseUrl)
                .defaultHeader("x-apisports-key", afApiKey)
                .build();
    }

    private JsonNode executeFd(Function<WebClient, String> call) {
        try {
            String response = call.apply(fdClient());
            if (response == null || response.isBlank()) return objectMapper.createObjectNode();
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429)                       log.warn("⚠️ [FD] Rate limit hit");
            else if (status == 401 || status == 403) log.error("❌ [FD] Auth error — check api.football.key");
            else                                     log.error("❌ [FD] HTTP {}: {}", status, e.getMessage());
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("❌ [FD] Unexpected error: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode executeAf(Function<WebClient, String> call) {
        try {
            String response = call.apply(afClient());
            if (response == null || response.isBlank()) return objectMapper.createObjectNode();
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429)                       log.warn("⚠️ [AF] Rate limit hit — free tier is 10 req/min");
            else if (status == 401 || status == 403) log.error("❌ [AF] Auth error — check api.apifootball.key");
            else                                     log.error("❌ [AF] HTTP {}: {}", status, e.getMessage());
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("❌ [AF] Unexpected error: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private boolean isApiFootballEnabled() {
        return afApiKey != null && !afApiKey.isBlank();
    }


    // ═══════════════════════════════════════════════════════════
    // REMAINING METHODS (football-data.org)
    // ═══════════════════════════════════════════════════════════

    public JsonNode getFixturesByDate(String date, int leagueId, int season) {
        String code = mapLeagueIdToCode(leagueId);
        if (code == null) {
            log.warn("⚠️ No competition code for leagueId: {}", leagueId);
            return objectMapper.createObjectNode();
        }
        return executeFd(c -> c.get()
                .uri(u -> u.path("/competitions/{code}/matches")
                        .queryParam("dateFrom", date)
                        .queryParam("dateTo", date)
                        .build(code))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getHeadToHead(int matchId) {
        return executeFd(c -> c.get()
                .uri("/matches/{id}/head2head", matchId)
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getStandings(int leagueId, int season) {
        String code = mapLeagueIdToCode(leagueId);
        if (code == null) return objectMapper.createObjectNode();
        return executeFd(c -> c.get()
                .uri(u -> u.path("/competitions/{code}/standings")
                        .queryParam("season", season)
                        .build(code))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getTeamLastMatches(int teamId, int count) {
        return executeFd(c -> c.get()
                .uri(u -> u.path("/teams/{id}/matches")
                        .queryParam("status", "FINISHED")
                        .queryParam("limit", count)
                        .build(teamId))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getTeamUpcomingMatches(int teamId) {
        return executeFd(c -> c.get()
                .uri(u -> u.path("/teams/{id}/matches")
                        .queryParam("status", "SCHEDULED")
                        .build(teamId))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getTeamStats(int leagueId, int season, int teamId) {
        return executeFd(c -> c.get()
                .uri("/teams/{id}", teamId)
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getTopScorers(int leagueId, int season) {
        String code = mapLeagueIdToCode(leagueId);
        if (code == null) return objectMapper.createObjectNode();
        return executeFd(c -> c.get()
                .uri(u -> u.path("/competitions/{code}/scorers")
                        .queryParam("season", season)
                        .build(code))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getActiveLeagues() {
        return executeFd(c -> c.get()
                .uri("/competitions")
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getNextFixtures(int count)              { return getMatchesByDateRange(LocalDate.now().toString(), LocalDate.now().plusDays(7).toString()); }
    public JsonNode getMatchEvents(int fixtureId)           { return getFixtureById(fixtureId); }
    public JsonNode getMatchLineups(int fixtureId)          { return getFixtureById(fixtureId); }
    public JsonNode getMatchStatistics(int fixtureId)       { return getFixtureById(fixtureId); }
    public JsonNode getMatchPlayerStats(int fixtureId)      { return getFixtureById(fixtureId); }
    public JsonNode getTeamInjuries(int teamId, int season) { return objectMapper.createObjectNode(); }
    public JsonNode getPredictions(int fixtureId)           { return objectMapper.createObjectNode(); }
    public JsonNode getApiStatus()                          { return getActiveLeagues(); }
    public JsonNode getHeadToHead(int t1, int t2)           { return objectMapper.createObjectNode(); }

    public String mapLeagueIdToCode(int leagueId) {
        return switch (leagueId) {
            case 39  -> "PL";
            case 140 -> "PD";
            case 135 -> "SA";
            case 78  -> "BL1";
            case 61  -> "FL1";
            case 2   -> "CL";
            case 3   -> "EL";
            default  -> null;
        };
    }
}