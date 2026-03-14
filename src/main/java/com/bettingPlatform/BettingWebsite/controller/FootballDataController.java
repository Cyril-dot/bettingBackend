package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.AdminPrincipal;
import com.bettingPlatform.BettingWebsite.dto.ApiResponse;
import com.bettingPlatform.BettingWebsite.service.ApiFootballClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/football")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FootballDataController {

    private final ApiFootballClient apiFootballClient;

    // ─────────────────────────────────────────────────────────────
    // Fixtures
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/football/fixtures/today
     * All matches scheduled or in play today across all competitions.
     */
    @GetMapping("/fixtures/today")
    public ResponseEntity<ApiResponse<JsonNode>> getTodayMatches(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {

        log.info("[FOOTBALL][FIXTURES] Fetching today's matches → requestedBy={}", adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getTodayMatches();

            log.info("[FOOTBALL][FIXTURES] Today's matches fetched → requestedBy={}", adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Today's matches", data));

        } catch (Exception e) {
            log.error("[FOOTBALL][FIXTURES] Failed to fetch today's matches → reason={} requestedBy={}",
                    e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch today's matches: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/football/fixtures/date?date=2024-12-01&leagueId=39&season=2024
     * Fixtures for a specific date, league, and season.
     */
    @GetMapping("/fixtures/date")
    public ResponseEntity<ApiResponse<JsonNode>> getFixturesByDate(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam String date,
            @RequestParam int leagueId,
            @RequestParam int season) {

        log.info("[FOOTBALL][FIXTURES] Fetching fixtures by date → date={} leagueId={} season={} requestedBy={}",
                date, leagueId, season, adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getFixturesByDate(date, leagueId, season);

            log.info("[FOOTBALL][FIXTURES] Fixtures fetched → date={} leagueId={} requestedBy={}",
                    date, leagueId, adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Fixtures for " + date, data));

        } catch (Exception e) {
            log.error("[FOOTBALL][FIXTURES] Failed to fetch fixtures → date={} leagueId={} reason={} requestedBy={}",
                    date, leagueId, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch fixtures: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/football/fixtures/range?dateFrom=2024-12-01&dateTo=2024-12-07
     * All matches across a date range.
     */
    @GetMapping("/fixtures/range")
    public ResponseEntity<ApiResponse<JsonNode>> getFixturesByDateRange(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam String dateFrom,
            @RequestParam String dateTo) {

        log.info("[FOOTBALL][FIXTURES] Fetching fixtures by range → from={} to={} requestedBy={}",
                dateFrom, dateTo, adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getMatchesByDateRange(dateFrom, dateTo);

            log.info("[FOOTBALL][FIXTURES] Range fixtures fetched → from={} to={} requestedBy={}",
                    dateFrom, dateTo, adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Fixtures from " + dateFrom + " to " + dateTo, data));

        } catch (Exception e) {
            log.error("[FOOTBALL][FIXTURES] Failed to fetch range fixtures → from={} to={} reason={} requestedBy={}",
                    dateFrom, dateTo, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch fixtures: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/football/fixtures/{fixtureId}
     * Single fixture by ID.
     */
    @GetMapping("/fixtures/{fixtureId}")
    public ResponseEntity<ApiResponse<JsonNode>> getFixtureById(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable int fixtureId) {

        log.info("[FOOTBALL][FIXTURES] Fetching fixture → fixtureId={} requestedBy={}",
                fixtureId, adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getFixtureById(fixtureId);

            log.info("[FOOTBALL][FIXTURES] Fixture fetched → fixtureId={} requestedBy={}",
                    fixtureId, adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Fixture " + fixtureId, data));

        } catch (Exception e) {
            log.error("[FOOTBALL][FIXTURES] Failed to fetch fixture → fixtureId={} reason={} requestedBy={}",
                    fixtureId, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch fixture: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Live Scores
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/football/live
     * All currently live / in-play fixtures.
     */
    @GetMapping("/live")
    public ResponseEntity<ApiResponse<JsonNode>> getLiveFixtures(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {

        log.info("[FOOTBALL][LIVE] Fetching live fixtures → requestedBy={}", adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getLiveFixtures();

            log.info("[FOOTBALL][LIVE] Live fixtures fetched → requestedBy={}", adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Live fixtures", data));

        } catch (Exception e) {
            log.error("[FOOTBALL][LIVE] Failed to fetch live fixtures → reason={} requestedBy={}",
                    e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch live fixtures: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Standings
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/football/standings?leagueId=39&season=2024
     * League table / standings for a competition and season.
     */
    @GetMapping("/standings")
    public ResponseEntity<ApiResponse<JsonNode>> getStandings(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam int leagueId,
            @RequestParam int season) {

        log.info("[FOOTBALL][STANDINGS] Fetching standings → leagueId={} season={} requestedBy={}",
                leagueId, season, adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getStandings(leagueId, season);

            log.info("[FOOTBALL][STANDINGS] Standings fetched → leagueId={} season={} requestedBy={}",
                    leagueId, season, adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Standings", data));

        } catch (Exception e) {
            log.error("[FOOTBALL][STANDINGS] Failed to fetch standings → leagueId={} season={} reason={} requestedBy={}",
                    leagueId, season, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch standings: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Top Scorers
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/football/scorers?leagueId=39&season=2024
     * Top scorers for a competition and season.
     */
    @GetMapping("/scorers")
    public ResponseEntity<ApiResponse<JsonNode>> getTopScorers(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @RequestParam int leagueId,
            @RequestParam int season) {

        log.info("[FOOTBALL][SCORERS] Fetching top scorers → leagueId={} season={} requestedBy={}",
                leagueId, season, adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getTopScorers(leagueId, season);

            log.info("[FOOTBALL][SCORERS] Top scorers fetched → leagueId={} season={} requestedBy={}",
                    leagueId, season, adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Top scorers", data));

        } catch (Exception e) {
            log.error("[FOOTBALL][SCORERS] Failed to fetch top scorers → leagueId={} season={} reason={} requestedBy={}",
                    leagueId, season, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch top scorers: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Teams
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/football/teams/{teamId}/matches/recent?count=10
     * Last N finished matches for a team.
     */
    @GetMapping("/teams/{teamId}/matches/recent")
    public ResponseEntity<ApiResponse<JsonNode>> getTeamRecentMatches(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable int teamId,
            @RequestParam(defaultValue = "10") int count) {

        log.info("[FOOTBALL][TEAMS] Fetching recent matches → teamId={} count={} requestedBy={}",
                teamId, count, adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getTeamLastMatches(teamId, count);

            log.info("[FOOTBALL][TEAMS] Recent matches fetched → teamId={} requestedBy={}",
                    teamId, adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Recent matches for team " + teamId, data));

        } catch (Exception e) {
            log.error("[FOOTBALL][TEAMS] Failed to fetch recent matches → teamId={} reason={} requestedBy={}",
                    teamId, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch team matches: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/football/teams/{teamId}/matches/upcoming
     * Upcoming scheduled matches for a team.
     */
    @GetMapping("/teams/{teamId}/matches/upcoming")
    public ResponseEntity<ApiResponse<JsonNode>> getTeamUpcomingMatches(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable int teamId) {

        log.info("[FOOTBALL][TEAMS] Fetching upcoming matches → teamId={} requestedBy={}",
                teamId, adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getTeamUpcomingMatches(teamId);

            log.info("[FOOTBALL][TEAMS] Upcoming matches fetched → teamId={} requestedBy={}",
                    teamId, adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Upcoming matches for team " + teamId, data));

        } catch (Exception e) {
            log.error("[FOOTBALL][TEAMS] Failed to fetch upcoming matches → teamId={} reason={} requestedBy={}",
                    teamId, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch upcoming matches: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/football/teams/{teamId}/stats?leagueId=39&season=2024
     * Season stats for a team in a given competition.
     */
    @GetMapping("/teams/{teamId}/stats")
    public ResponseEntity<ApiResponse<JsonNode>> getTeamStats(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable int teamId,
            @RequestParam int leagueId,
            @RequestParam int season) {

        log.info("[FOOTBALL][TEAMS] Fetching team stats → teamId={} leagueId={} season={} requestedBy={}",
                teamId, leagueId, season, adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getTeamStats(leagueId, season, teamId);

            log.info("[FOOTBALL][TEAMS] Team stats fetched → teamId={} requestedBy={}",
                    teamId, adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Stats for team " + teamId, data));

        } catch (Exception e) {
            log.error("[FOOTBALL][TEAMS] Failed to fetch team stats → teamId={} reason={} requestedBy={}",
                    teamId, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch team stats: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Head to Head
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/football/h2h/{matchId}
     * Head-to-head history for a given match ID.
     */
    @GetMapping("/h2h/{matchId}")
    public ResponseEntity<ApiResponse<JsonNode>> getHeadToHead(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable int matchId) {

        log.info("[FOOTBALL][H2H] Fetching H2H → matchId={} requestedBy={}",
                matchId, adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getHeadToHead(matchId);

            log.info("[FOOTBALL][H2H] H2H fetched → matchId={} requestedBy={}",
                    matchId, adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Head to head for match " + matchId, data));

        } catch (Exception e) {
            log.error("[FOOTBALL][H2H] Failed to fetch H2H → matchId={} reason={} requestedBy={}",
                    matchId, e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch H2H: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Competitions
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/football/competitions
     * All active / subscribed competitions.
     */
    @GetMapping("/competitions")
    public ResponseEntity<ApiResponse<JsonNode>> getActiveLeagues(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {

        log.info("[FOOTBALL][COMPETITIONS] Fetching active competitions → requestedBy={}", adminPrincipal.getSellerId());

        try {
            JsonNode data = apiFootballClient.getActiveLeagues();

            log.info("[FOOTBALL][COMPETITIONS] Competitions fetched → requestedBy={}", adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Active competitions", data));

        } catch (Exception e) {
            log.error("[FOOTBALL][COMPETITIONS] Failed to fetch competitions → reason={} requestedBy={}",
                    e.getMessage(), adminPrincipal.getSellerId(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to fetch competitions: " + e.getMessage()));
        }
    }
}