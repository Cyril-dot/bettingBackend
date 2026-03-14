package com.bettingPlatform.BettingWebsite.service;

import com.bettingPlatform.BettingWebsite.dto.MatchAnalyticsResponse;
import com.bettingPlatform.BettingWebsite.entity.Game;
import com.bettingPlatform.BettingWebsite.entity.repos.GameRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class AiAnalyticsService {

    private final ApiFootballClient apiFootballClient;
    private final OddsApiClient     oddsApiClient;
    private final ObjectMapper      objectMapper;
    private final RestTemplate      restTemplate;
    private final GameRepo gameRepository;

    @Value("${asi.api.key}")
    private String asiApiKey;

    private static final String ASI_URL  = "https://api.asi1.ai/v1/chat/completions";
    private static final String MODEL    = "asi1";
    private static final String DDGO_URL = "https://api.duckduckgo.com/";
    private static final String WIKI_URL = "https://en.wikipedia.org/api/rest_v1/page/summary/";

    public AiAnalyticsService(ApiFootballClient apiFootballClient,
                              OddsApiClient oddsApiClient,
                              ObjectMapper objectMapper,
                              RestTemplate restTemplate,
                              GameRepo gameRepository) {
        this.apiFootballClient = apiFootballClient;
        this.oddsApiClient     = oddsApiClient;
        this.objectMapper      = objectMapper;
        this.restTemplate      = restTemplate;
        this.gameRepository    = gameRepository;
    }


    // ═══════════════════════════════════════════════════════════
    // MAIN ENTRY POINT — accepts only UUID, resolves IDs internally
    // ═══════════════════════════════════════════════════════════

    public MatchAnalyticsResponse analyzeMatch(UUID gameId) {

        // ── 1. Load game from DB ──────────────────────────────
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("No game found with id: " + gameId));

        // ── 2. Resolve numeric fixture ID ─────────────────────
        // externalFixtureId is "af-1530170" or plain "554784"
        String extId = game.getExternalFixtureId();
        if (extId == null || extId.isBlank()) {
            throw new IllegalArgumentException("Game has no externalFixtureId set.");
        }
        int fixtureId;
        try {
            fixtureId = Integer.parseInt(extId.replaceAll("^[a-zA-Z]+-", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse fixture ID from: " + extId);
        }


        String storedHomeName = game.getHomeTeam() != null ? game.getHomeTeam() : "Home Team";
        String storedAwayName = game.getAwayTeam() != null ? game.getAwayTeam() : "Away Team";

        log.info("🤖 AI analysis → gameId={} fixtureId={} home='{}' away='{}'",
                gameId, fixtureId, storedHomeName, storedAwayName);

        return runAnalysis(fixtureId,storedHomeName, storedAwayName);
    }


    // ═══════════════════════════════════════════════════════════
    // CORE ANALYSIS LOGIC
    // ═══════════════════════════════════════════════════════════
    private MatchAnalyticsResponse runAnalysis(
            int fixtureId,
            String fallbackHomeName,
            String fallbackAwayName) {

        // Fetch structured data
        JsonNode apiPrediction = apiFootballClient.getPredictions(fixtureId);
        JsonNode odds = oddsApiClient.getOddsForEvent(String.valueOf(fixtureId));

        // Since team stats were removed, use neutral baseline values
        double homeWinRate = 0.5;
        double awayWinRate = 0.5;
        double homeAvgGoals = 1.5;
        double awayAvgGoals = 1.5;
        double homeAvgConceded = 1.2;
        double awayAvgConceded = 1.2;
        double bttsRate = 0.5;
        double over25Rate = 0.5;

        // Simple prediction baseline
        String localPrediction = "DRAW";
        double localConfidence = 50.0;

        // Use stored team names
        String homeTeamName = fallbackHomeName;
        String awayTeamName = fallbackAwayName;

        log.info("🌐 Web search for: {} vs {}", homeTeamName, awayTeamName);

        String homeNews  = searchDuckDuckGo(homeTeamName + " football form injury news 2026");
        String awayNews  = searchDuckDuckGo(awayTeamName + " football form injury news 2026");
        String matchNews = searchDuckDuckGo(homeTeamName + " vs " + awayTeamName + " prediction 2026");

        String homeWiki  = searchWikipedia(homeTeamName);
        String awayWiki  = searchWikipedia(awayTeamName);

        log.info("✅ Web context ready — calling ASI:One for fixture {}", fixtureId);

        String systemPrompt = """
            You are an elite football analytics AI and betting prediction engine.
            You have access to both structured match statistics and real-world web context.
            Your job is to produce sharp, data-driven match analysis and a confident single betting tip.
            Always return valid JSON only — no preamble, no text outside the JSON object.
            """;

        String userMessage = """
            Analyse this football match and return a JSON prediction.

            === MATCH STATS ===
            Home Team: %s
            Away Team: %s
            Fixture ID: %d

            Home Win Rate (baseline): %.1f%%
            Away Win Rate (baseline): %.1f%%
            Home Avg Goals Scored: %.2f
            Away Avg Goals Scored: %.2f
            Home Avg Goals Conceded: %.2f
            Away Avg Goals Conceded: %.2f
            BTTS Rate: %.1f%%
            Over 2.5 Rate: %.1f%%
            Local Model Prediction: %s (confidence: %.1f%%)

            === WEB CONTEXT ===
            %s recent news/form: %s

            %s recent news/form: %s

            Head-to-head / match context: %s

            %s Wikipedia summary: %s

            %s Wikipedia summary: %s

            === REQUIRED JSON RESPONSE FORMAT ===
            {
              "predictedOutcome": "HOME WIN | AWAY WIN | DRAW",
              "confidencePercent": 0.0,
              "suggestedTip": "your single best betting tip",
              "riskLevel": "LOW RISK | MEDIUM RISK | HIGH RISK",
              "homeWinRate": 0.0,
              "awayWinRate": 0.0,
              "drawRate": 0.0,
              "homeAvgGoalsScored": 0.0,
              "awayAvgGoalsScored": 0.0,
              "homeAvgGoalsConceded": 0.0,
              "awayAvgGoalsConceded": 0.0,
              "bttsRate": 0.0,
              "over25Rate": 0.0,
              "aiInsight": "2-3 sentence expert analysis"
            }
            """.formatted(
                homeTeamName, awayTeamName, fixtureId,
                homeWinRate * 100, awayWinRate * 100,
                homeAvgGoals, awayAvgGoals,
                homeAvgConceded, awayAvgConceded,
                bttsRate * 100, over25Rate * 100,
                localPrediction, localConfidence,
                homeTeamName, homeNews,
                awayTeamName, awayNews,
                matchNews,
                homeTeamName, homeWiki,
                awayTeamName, awayWiki
        );

        String aiReply = callAsi(systemPrompt, userMessage);
        log.info("🤖 ASI:One response received for fixture {}", fixtureId);

        return parseAiResponse(
                aiReply,
                fixtureId,
                homeWinRate,
                awayWinRate,
                homeAvgGoals,
                awayAvgGoals,
                homeAvgConceded,
                awayAvgConceded,
                bttsRate,
                over25Rate,
                localPrediction,
                localConfidence
        );
    }


    // ═══════════════════════════════════════════════════════════
    // ASI:ONE API
    // ═══════════════════════════════════════════════════════════

    private String callAsi(String systemPrompt, String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(asiApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userMessage)
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(ASI_URL, entity, Map.class);
            List<Map> choices = (List<Map>) response.getBody().get("choices");
            Map message = (Map) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            log.error("❌ ASI:One API call failed: {}", e.getMessage());
            throw new RuntimeException("AI service unavailable. Please try again.");
        }
    }


    // ═══════════════════════════════════════════════════════════
    // FREE WEB SEARCH 1 — DuckDuckGo
    // ═══════════════════════════════════════════════════════════

    private String searchDuckDuckGo(String query) {
        try {
            String url = DDGO_URL + "?q=" + query.replace(" ", "+")
                    + "&format=json&no_html=1&skip_disambig=1";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map body = response.getBody();
            if (body == null) return "No data found.";
            String abstract_ = (String) body.getOrDefault("AbstractText", "");
            if (abstract_ != null && !abstract_.isBlank()) return abstract_;
            List<Map> related = (List<Map>) body.getOrDefault("RelatedTopics", List.of());
            if (!related.isEmpty()) {
                String text = (String) related.get(0).getOrDefault("Text", "");
                if (text != null && !text.isBlank()) return text;
            }
            return "No recent context found.";
        } catch (Exception e) {
            log.warn("⚠️ DuckDuckGo search failed for '{}': {}", query, e.getMessage());
            return "Search unavailable.";
        }
    }


    // ═══════════════════════════════════════════════════════════
    // FREE WEB SEARCH 2 — Wikipedia
    // ═══════════════════════════════════════════════════════════

    private String searchWikipedia(String teamName) {
        try {
            String encoded = teamName.replace(" ", "_");
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    WIKI_URL + encoded, Map.class);
            Map body = response.getBody();
            if (body == null) return "No Wikipedia data.";
            String extract = (String) body.getOrDefault("extract", "");
            if (extract != null && extract.length() > 500) extract = extract.substring(0, 500) + "...";
            return extract != null && !extract.isBlank() ? extract : "No Wikipedia summary available.";
        } catch (Exception e) {
            log.warn("⚠️ Wikipedia search failed for '{}': {}", teamName, e.getMessage());
            return "Wikipedia data unavailable.";
        }
    }


    // ═══════════════════════════════════════════════════════════
    // PARSE ASI RESPONSE
    // ═══════════════════════════════════════════════════════════

    private MatchAnalyticsResponse parseAiResponse(String aiReply,
                                                   int fixtureId,
                                                   double homeWinRate,
                                                   double awayWinRate,
                                                   double homeAvgGoals,
                                                   double awayAvgGoals,
                                                   double homeAvgConceded,
                                                   double awayAvgConceded,
                                                   double bttsRate,
                                                   double over25Rate,
                                                   String fallbackPrediction,
                                                   double fallbackConfidence) {
        try {
            String json = aiReply.trim()
                    .replaceAll("(?s)```json", "")
                    .replaceAll("(?s)```", "")
                    .trim();

            JsonNode node = objectMapper.readTree(json);

            return MatchAnalyticsResponse.builder()
                    .fixtureId(fixtureId)
                    .predictedOutcome(node.path("predictedOutcome").asText(fallbackPrediction))
                    .confidencePercent(node.path("confidencePercent").asDouble(fallbackConfidence))
                    .suggestedTip(node.path("suggestedTip").asText("No tip available"))
                    .riskLevel(node.path("riskLevel").asText(getRiskLevel(fallbackConfidence)))
                    .homeWinRate(node.path("homeWinRate").asDouble(round(homeWinRate * 100)))
                    .awayWinRate(node.path("awayWinRate").asDouble(round(awayWinRate * 100)))
                    .drawRate(node.path("drawRate").asDouble(round((1 - homeWinRate - awayWinRate) * 100)))
                    .homeAvgGoalsScored(node.path("homeAvgGoalsScored").asDouble(round(homeAvgGoals)))
                    .awayAvgGoalsScored(node.path("awayAvgGoalsScored").asDouble(round(awayAvgGoals)))
                    .homeAvgGoalsConceded(node.path("homeAvgGoalsConceded").asDouble(round(homeAvgConceded)))
                    .awayAvgGoalsConceded(node.path("awayAvgGoalsConceded").asDouble(round(awayAvgConceded)))
                    .bttsRate(node.path("bttsRate").asDouble(round(bttsRate * 100)))
                    .over25Rate(node.path("over25Rate").asDouble(round(over25Rate * 100)))
                    .aiInsight(node.path("aiInsight").asText("Analysis not available"))
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to parse ASI response — using local fallback: {}", e.getMessage());

            String tip = generateTip(homeAvgGoals, awayAvgGoals,
                    homeAvgConceded, awayAvgConceded,
                    bttsRate, over25Rate, fallbackPrediction);

            return MatchAnalyticsResponse.builder()
                    .fixtureId(fixtureId)
                    .homeWinRate(round(homeWinRate * 100))
                    .awayWinRate(round(awayWinRate * 100))
                    .drawRate(round((1 - homeWinRate - awayWinRate) * 100))
                    .homeAvgGoalsScored(round(homeAvgGoals))
                    .awayAvgGoalsScored(round(awayAvgGoals))
                    .homeAvgGoalsConceded(round(homeAvgConceded))
                    .awayAvgGoalsConceded(round(awayAvgConceded))
                    .bttsRate(round(bttsRate * 100))
                    .over25Rate(round(over25Rate * 100))
                    .predictedOutcome(fallbackPrediction)
                    .confidencePercent(round(fallbackConfidence))
                    .suggestedTip(tip)
                    .riskLevel(getRiskLevel(fallbackConfidence))
                    .aiInsight("AI analysis unavailable — showing statistical prediction.")
                    .build();
        }
    }


    // ═══════════════════════════════════════════════════════════
    // STAT CALCULATORS
    // ═══════════════════════════════════════════════════════════

    private double calculateWinRate(JsonNode fixtures, int teamId) {
        if (fixtures == null || !fixtures.has("response")) return 0.5;
        JsonNode matches = fixtures.get("response");
        int total = 0, wins = 0;
        for (JsonNode match : matches) {
            JsonNode goals = match.path("goals");
            JsonNode teams = match.path("teams");
            boolean isHome = teams.path("home").path("id").asInt() == teamId;
            int scored   = isHome ? goals.path("home").asInt() : goals.path("away").asInt();
            int conceded = isHome ? goals.path("away").asInt() : goals.path("home").asInt();
            total++;
            if (scored > conceded) wins++;
        }
        return total == 0 ? 0.5 : (double) wins / total;
    }

    private double calculateAvgGoalsScored(JsonNode fixtures, int teamId) {
        if (fixtures == null || !fixtures.has("response")) return 1.5;
        JsonNode matches = fixtures.get("response");
        int total = 0; double sum = 0;
        for (JsonNode match : matches) {
            JsonNode goals = match.path("goals");
            JsonNode teams = match.path("teams");
            boolean isHome = teams.path("home").path("id").asInt() == teamId;
            sum += isHome ? goals.path("home").asInt() : goals.path("away").asInt();
            total++;
        }
        return total == 0 ? 1.5 : sum / total;
    }

    private double calculateAvgGoalsConceded(JsonNode fixtures, int teamId) {
        if (fixtures == null || !fixtures.has("response")) return 1.0;
        JsonNode matches = fixtures.get("response");
        int total = 0; double sum = 0;
        for (JsonNode match : matches) {
            JsonNode goals = match.path("goals");
            JsonNode teams = match.path("teams");
            boolean isHome = teams.path("home").path("id").asInt() == teamId;
            sum += isHome ? goals.path("away").asInt() : goals.path("home").asInt();
            total++;
        }
        return total == 0 ? 1.0 : sum / total;
    }

    private double calculateBttsRate(JsonNode h2h) {
        if (h2h == null || !h2h.has("response")) return 0.5;
        JsonNode matches = h2h.get("response");
        int total = 0, btts = 0;
        for (JsonNode match : matches) {
            JsonNode goals = match.path("goals");
            total++;
            if (goals.path("home").asInt() > 0 && goals.path("away").asInt() > 0) btts++;
        }
        return total == 0 ? 0.5 : (double) btts / total;
    }

    private double calculateOver25Rate(JsonNode h2h) {
        if (h2h == null || !h2h.has("response")) return 0.5;
        JsonNode matches = h2h.get("response");
        int total = 0, over = 0;
        for (JsonNode match : matches) {
            JsonNode goals = match.path("goals");
            total++;
            if (goals.path("home").asInt() + goals.path("away").asInt() > 2) over++;
        }
        return total == 0 ? 0.5 : (double) over / total;
    }

    private double getSeasonWinRate(JsonNode stats) {
        if (stats == null || !stats.has("response")) return 0.5;
        try {
            JsonNode fixtures = stats.path("response").path("fixtures");
            int wins   = fixtures.path("wins").path("total").asInt();
            int played = fixtures.path("played").path("total").asInt();
            return played == 0 ? 0.5 : (double) wins / played;
        } catch (Exception e) { return 0.5; }
    }

    private double getH2HWinRate(JsonNode h2h, int teamId) {
        if (h2h == null || !h2h.has("response")) return 0.5;
        JsonNode matches = h2h.get("response");
        int total = 0, wins = 0;
        for (JsonNode match : matches) {
            JsonNode teams = match.path("teams");
            JsonNode goals = match.path("goals");
            boolean isHome = teams.path("home").path("id").asInt() == teamId;
            int scored   = isHome ? goals.path("home").asInt() : goals.path("away").asInt();
            int conceded = isHome ? goals.path("away").asInt() : goals.path("home").asInt();
            total++;
            if (scored > conceded) wins++;
        }
        return total == 0 ? 0.5 : (double) wins / total;
    }

    private String extractTeamName(JsonNode fixtures, int teamId) {
        if (fixtures == null || !fixtures.has("response")) return "Team " + teamId;
        for (JsonNode match : fixtures.get("response")) {
            JsonNode teams = match.path("teams");
            if (teams.path("home").path("id").asInt() == teamId)
                return teams.path("home").path("name").asText("Team " + teamId);
            if (teams.path("away").path("id").asInt() == teamId)
                return teams.path("away").path("name").asText("Team " + teamId);
        }
        return "Team " + teamId;
    }

    private String generateTip(double homeAvgGoals, double awayAvgGoals,
                               double homeAvgConceded, double awayAvgConceded,
                               double bttsRate, double over25Rate,
                               String predictedOutcome) {
        List<String> tips = new ArrayList<>();
        if (over25Rate > 0.6)  tips.add("Over 2.5 Goals");
        if (over25Rate < 0.35) tips.add("Under 2.5 Goals");
        if (bttsRate > 0.65)   tips.add("BTTS - Yes");
        if (bttsRate < 0.3)    tips.add("BTTS - No");
        double expected = (homeAvgGoals + awayAvgConceded + awayAvgGoals + homeAvgConceded) / 2;
        if (expected > 3.0)    tips.add("Over 1.5 Goals");
        tips.add(predictedOutcome.equals("HOME WIN") ? "1x2 - Home Win"
                : predictedOutcome.equals("AWAY WIN") ? "1x2 - Away Win"
                : "Double Chance - Draw or Home");
        return tips.isEmpty() ? predictedOutcome : tips.get(0);
    }

    private String getRiskLevel(double confidence) {
        if (confidence >= 70) return "LOW RISK";
        if (confidence >= 45) return "MEDIUM RISK";
        return "HIGH RISK";
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}