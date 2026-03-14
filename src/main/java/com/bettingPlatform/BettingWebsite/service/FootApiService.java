package com.bettingPlatform.BettingWebsite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class FootApiService {

    @Value("${api.foot.key}")
    private String apiKey;

    @Value("${api.foot.base-url}")
    private String baseUrl;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public FootApiService(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    private WebClient client() {
        return webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("x-apisports-key", apiKey)
                .build();
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to parse API-Football response: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    // ── Timezone ──────────────────────────────────────────────────

    public JsonNode getTimezones() {
        log.info("🕐 Fetching timezones");
        String response = client().get()
                .uri("/timezone")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Countries ─────────────────────────────────────────────────

    public JsonNode getCountries() {
        log.info("🌍 Fetching all countries");
        String response = client().get()
                .uri("/countries")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getCountryByName(String name) {
        log.info("🌍 Fetching country: {}", name);
        String response = client().get()
                .uri(u -> u.path("/countries").queryParam("name", name).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getCountryByCode(String code) {
        log.info("🌍 Fetching country by code: {}", code);
        String response = client().get()
                .uri(u -> u.path("/countries").queryParam("code", code).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Leagues ───────────────────────────────────────────────────

    public JsonNode getLeagues() {
        log.info("🏆 Fetching all leagues");
        String response = client().get()
                .uri("/leagues")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getLeagueById(int leagueId) {
        log.info("🏆 Fetching league: {}", leagueId);
        String response = client().get()
                .uri(u -> u.path("/leagues").queryParam("id", leagueId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getCurrentLeagues() {
        log.info("🏆 Fetching current leagues");
        String response = client().get()
                .uri(u -> u.path("/leagues").queryParam("current", "true").build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getLeaguesByCountry(String country) {
        log.info("🏆 Fetching leagues for country: {}", country);
        String response = client().get()
                .uri(u -> u.path("/leagues").queryParam("country", country).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getLeagueSeasons() {
        log.info("📅 Fetching all league seasons");
        String response = client().get()
                .uri("/leagues/seasons")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Teams ─────────────────────────────────────────────────────

    public JsonNode getTeamById(int teamId) {
        log.info("👥 Fetching team: {}", teamId);
        String response = client().get()
                .uri(u -> u.path("/teams").queryParam("id", teamId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getTeamsByLeagueAndSeason(int leagueId, int season) {
        log.info("👥 Fetching teams — league: {}, season: {}", leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/teams")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode searchTeam(String name) {
        log.info("🔍 Searching team: {}", name);
        String response = client().get()
                .uri(u -> u.path("/teams").queryParam("search", name).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getTeamStatistics(int leagueId, int season, int teamId) {
        log.info("📈 Fetching team stats — team: {}, league: {}, season: {}", teamId, leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/teams/statistics")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .queryParam("team", teamId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getTeamSeasons(int teamId) {
        log.info("📅 Fetching seasons for team: {}", teamId);
        String response = client().get()
                .uri(u -> u.path("/teams/seasons").queryParam("team", teamId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Venues ────────────────────────────────────────────────────

    public JsonNode getVenueById(int venueId) {
        log.info("🏟️ Fetching venue: {}", venueId);
        String response = client().get()
                .uri(u -> u.path("/venues").queryParam("id", venueId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode searchVenue(String name) {
        log.info("🔍 Searching venue: {}", name);
        String response = client().get()
                .uri(u -> u.path("/venues").queryParam("search", name).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Standings ─────────────────────────────────────────────────

    public JsonNode getStandings(int leagueId, int season) {
        log.info("📊 Fetching standings — league: {}, season: {}", leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/standings")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getTeamStandings(int teamId, int season) {
        log.info("📊 Fetching standings for team: {}, season: {}", teamId, season);
        String response = client().get()
                .uri(u -> u.path("/standings")
                        .queryParam("team", teamId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Fixtures ──────────────────────────────────────────────────

    public JsonNode getFixtureById(int fixtureId) {
        log.info("⚽ Fetching fixture: {}", fixtureId);
        String response = client().get()
                .uri(u -> u.path("/fixtures").queryParam("id", fixtureId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getLiveFixtures() {
        log.info("🔴 Fetching all live fixtures");
        String response = client().get()
                .uri(u -> u.path("/fixtures").queryParam("live", "all").build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getLiveFixturesByLeagues(String leagueIds) {
        log.info("🔴 Fetching live fixtures for leagues: {}", leagueIds);
        String response = client().get()
                .uri(u -> u.path("/fixtures").queryParam("live", leagueIds).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getFixturesByDate(String date) {
        log.info("📅 Fetching fixtures for date: {}", date);
        String response = client().get()
                .uri(u -> u.path("/fixtures").queryParam("date", date).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getFixturesByLeagueAndSeason(int leagueId, int season) {
        log.info("📋 Fetching fixtures — league: {}, season: {}", leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/fixtures")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getNextFixtures(int count) {
        log.info("⏭️ Fetching next {} fixtures", count);
        String response = client().get()
                .uri(u -> u.path("/fixtures").queryParam("next", count).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getLastFixtures(int count) {
        log.info("⏮️ Fetching last {} fixtures", count);
        String response = client().get()
                .uri(u -> u.path("/fixtures").queryParam("last", count).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getFixtureRounds(int leagueId, int season) {
        log.info("🔄 Fetching rounds — league: {}, season: {}", leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/fixtures/rounds")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getFixtureEvents(int fixtureId) {
        log.info("📝 Fetching events for fixture: {}", fixtureId);
        String response = client().get()
                .uri(u -> u.path("/fixtures/events").queryParam("fixture", fixtureId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getFixtureLineups(int fixtureId) {
        log.info("📋 Fetching lineups for fixture: {}", fixtureId);
        String response = client().get()
                .uri(u -> u.path("/fixtures/lineups").queryParam("fixture", fixtureId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getFixtureStatistics(int fixtureId) {
        log.info("📊 Fetching statistics for fixture: {}", fixtureId);
        String response = client().get()
                .uri(u -> u.path("/fixtures/statistics").queryParam("fixture", fixtureId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getFixturePlayers(int fixtureId) {
        log.info("👤 Fetching player stats for fixture: {}", fixtureId);
        String response = client().get()
                .uri(u -> u.path("/fixtures/players").queryParam("fixture", fixtureId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getHeadToHead(int teamId1, int teamId2) {
        log.info("⚔️ Fetching H2H — {} vs {}", teamId1, teamId2);
        String response = client().get()
                .uri(u -> u.path("/fixtures/headtohead")
                        .queryParam("h2h", teamId1 + "-" + teamId2)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Injuries ──────────────────────────────────────────────────

    public JsonNode getInjuriesByFixture(int fixtureId) {
        log.info("🤕 Fetching injuries for fixture: {}", fixtureId);
        String response = client().get()
                .uri(u -> u.path("/injuries").queryParam("fixture", fixtureId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getInjuriesByLeagueAndSeason(int leagueId, int season) {
        log.info("🤕 Fetching injuries — league: {}, season: {}", leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/injuries")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Predictions ───────────────────────────────────────────────

    public JsonNode getPredictions(int fixtureId) {
        log.info("🔮 Fetching predictions for fixture: {}", fixtureId);
        String response = client().get()
                .uri(u -> u.path("/predictions").queryParam("fixture", fixtureId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Players ───────────────────────────────────────────────────

    public JsonNode getPlayerStatistics(int playerId, int season) {
        log.info("👤 Fetching player stats — player: {}, season: {}", playerId, season);
        String response = client().get()
                .uri(u -> u.path("/players")
                        .queryParam("id", playerId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getPlayersByTeamAndSeason(int teamId, int season) {
        log.info("👥 Fetching players — team: {}, season: {}", teamId, season);
        String response = client().get()
                .uri(u -> u.path("/players")
                        .queryParam("team", teamId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getSquadByTeam(int teamId) {
        log.info("👥 Fetching squad for team: {}", teamId);
        String response = client().get()
                .uri(u -> u.path("/players/squads").queryParam("team", teamId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getTopScorers(int leagueId, int season) {
        log.info("⚽ Fetching top scorers — league: {}, season: {}", leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/players/topscorers")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getTopAssists(int leagueId, int season) {
        log.info("🎯 Fetching top assists — league: {}, season: {}", leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/players/topassists")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getTopYellowCards(int leagueId, int season) {
        log.info("🟨 Fetching top yellow cards — league: {}, season: {}", leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/players/topyellowcards")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getTopRedCards(int leagueId, int season) {
        log.info("🟥 Fetching top red cards — league: {}, season: {}", leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/players/topredcards")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Transfers ─────────────────────────────────────────────────

    public JsonNode getTransfersByPlayer(int playerId) {
        log.info("🔄 Fetching transfers for player: {}", playerId);
        String response = client().get()
                .uri(u -> u.path("/transfers").queryParam("player", playerId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getTransfersByTeam(int teamId) {
        log.info("🔄 Fetching transfers for team: {}", teamId);
        String response = client().get()
                .uri(u -> u.path("/transfers").queryParam("team", teamId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Trophies ──────────────────────────────────────────────────

    public JsonNode getTrophiesByPlayer(int playerId) {
        log.info("🏆 Fetching trophies for player: {}", playerId);
        String response = client().get()
                .uri(u -> u.path("/trophies").queryParam("player", playerId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getTrophiesByCoach(int coachId) {
        log.info("🏆 Fetching trophies for coach: {}", coachId);
        String response = client().get()
                .uri(u -> u.path("/trophies").queryParam("coach", coachId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Sidelined ─────────────────────────────────────────────────

    public JsonNode getSidelinedByPlayer(int playerId) {
        log.info("🚑 Fetching sidelined for player: {}", playerId);
        String response = client().get()
                .uri(u -> u.path("/sidelined").queryParam("player", playerId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getSidelinedByCoach(int coachId) {
        log.info("🚑 Fetching sidelined for coach: {}", coachId);
        String response = client().get()
                .uri(u -> u.path("/sidelined").queryParam("coach", coachId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Coaches ───────────────────────────────────────────────────

    public JsonNode getCoachById(int coachId) {
        log.info("👔 Fetching coach: {}", coachId);
        String response = client().get()
                .uri(u -> u.path("/coachs").queryParam("id", coachId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getCoachByTeam(int teamId) {
        log.info("👔 Fetching coach for team: {}", teamId);
        String response = client().get()
                .uri(u -> u.path("/coachs").queryParam("team", teamId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Odds (Pre-Match) ──────────────────────────────────────────

    public JsonNode getPreMatchOdds(int fixtureId) {
        log.info("📡 [Fallback] Fetching pre-match odds for fixture: {}", fixtureId);
        String response = client().get()
                .uri(u -> u.path("/odds").queryParam("fixture", fixtureId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getPreMatchOddsByLeague(int leagueId, int season) {
        log.info("📡 Fetching pre-match odds — league: {}, season: {}", leagueId, season);
        String response = client().get()
                .uri(u -> u.path("/odds")
                        .queryParam("league", leagueId)
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getPreMatchOddsByDate(String date) {
        log.info("📡 Fetching pre-match odds for date: {}", date);
        String response = client().get()
                .uri(u -> u.path("/odds").queryParam("date", date).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getOddsBookmakers() {
        log.info("📚 Fetching all bookmakers");
        String response = client().get()
                .uri("/odds/bookmakers")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getOddsBets() {
        log.info("📚 Fetching all pre-match bet types");
        String response = client().get()
                .uri("/odds/bets")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getOddsMapping() {
        log.info("🗺️ Fetching odds mapping");
        String response = client().get()
                .uri("/odds/mapping")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Odds (In-Play / Live) ─────────────────────────────────────

    public JsonNode getLiveOdds() {
        log.info("📡 [Fallback] Fetching all live odds");
        String response = client().get()
                .uri("/odds/live")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getLiveOdds(int fixtureId) {
        log.info("📡 [Fallback] Fetching live odds for fixture: {}", fixtureId);
        String response = client().get()
                .uri(u -> u.path("/odds/live").queryParam("fixture", fixtureId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getLiveOddsByLeague(int leagueId) {
        log.info("📡 Fetching live odds for league: {}", leagueId);
        String response = client().get()
                .uri(u -> u.path("/odds/live").queryParam("league", leagueId).build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    public JsonNode getLiveOddsBets() {
        log.info("📚 Fetching live bet types");
        String response = client().get()
                .uri("/odds/live/bets")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }

    // ── Status (quota check) ──────────────────────────────────────

    public JsonNode getStatus() {
        log.info("📶 Checking API-Football quota status");
        String response = client().get()
                .uri("/status")
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parse(response);
    }
}