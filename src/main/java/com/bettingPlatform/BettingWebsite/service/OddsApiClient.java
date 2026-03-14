package com.bettingPlatform.BettingWebsite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

@Component
@Slf4j
public class OddsApiClient {

    // ── Add key2 and key3 to application.properties when accounts are ready ──
    @Value("${api.odds.key1}")
    private String apiKey1;

    @Value("${api.odds.key2:#{null}}")
    private String apiKey2;

    @Value("${api.odds.base-url}")
    private String baseUrl;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    public OddsApiClient(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    // ── Builds list of active (non-null) keys in round-robin order ────────────
    private List<String> getKeysInOrder() {
        List<String> active = new ArrayList<>();
        active.add(apiKey1);
        if (apiKey2 != null && !apiKey2.isBlank()) active.add(apiKey2);

        int start = keyIndex.getAndUpdate(i -> (i + 1) % active.size());
        List<String> ordered = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            ordered.add(active.get((start + i) % active.size()));
        }
        return ordered;
    }

    private WebClient client() {
        return webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    /**
     * Core fallback executor.
     * BiFunction receives (WebClient, apiKey) — each call injects the key as ?apiKey=
     * Falls back on 401, 403, 429, or any exception.
     */
    private JsonNode executeWithFallback(BiFunction<WebClient, String, String> apiCall) {
        List<String> keys = getKeysInOrder();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String label = "odds-key" + (i + 1);
            try {
                log.debug("🔑 Trying {}", label);
                String response = apiCall.apply(client(), key);
                if (response == null || response.isBlank() || response.equals("{}")) {
                    log.warn("⚠️ {} returned empty, trying next...", label);
                    continue;
                }
                return parse(response);
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 401 || status == 403 || status == 429) {
                    log.warn("⚠️ {} hit HTTP {} — quota or invalid key, falling back...", label, status);
                } else {
                    log.error("❌ {} failed HTTP {}: {}", label, status, e.getMessage());
                }
                if (i == keys.size() - 1) log.error("🚨 All Odds API keys exhausted!");
            } catch (Exception e) {
                log.warn("⚠️ {} threw: {}, trying next...", label, e.getMessage());
                if (i == keys.size() - 1) log.error("🚨 All Odds API keys exhausted!");
            }
        }
        return objectMapper.createObjectNode();
    }

    // ── Sports ────────────────────────────────────────────────────

    public JsonNode getInSeasonSports() {
        log.info("🏆 Fetching in-season sports");
        return executeWithFallback((client, key) -> client.get()
                .uri(u -> u.path("/sports")
                        .queryParam("apiKey", key)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    // ── Odds ──────────────────────────────────────────────────────

    public JsonNode getOddsForSport(String sportKey, String region) {
        log.info("📊 Fetching odds — sport: {}, region: {}", sportKey, region);
        return executeWithFallback((client, key) -> client.get()
                .uri(u -> u.path("/sports/{sport}/odds")
                        .queryParam("apiKey", key)
                        .queryParam("regions", region)
                        .queryParam("markets", "h2h,totals")
                        .queryParam("oddsFormat", "decimal")
                        .build(sportKey))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getFullOddsForSport(String sportKey, String region) {
        log.info("📊 Fetching full odds — sport: {}, region: {}", sportKey, region);
        return executeWithFallback((client, key) -> client.get()
                .uri(u -> u.path("/sports/{sport}/odds")
                        .queryParam("apiKey", key)
                        .queryParam("regions", region)
                        .queryParam("markets", "h2h,spreads,totals")
                        .queryParam("oddsFormat", "decimal")
                        .build(sportKey))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getOddsForEvent(String sportKey, String eventId, String region) {
        log.info("🎯 Fetching odds for event: {}", eventId);
        return executeWithFallback((client, key) -> client.get()
                .uri(u -> u.path("/sports/{sport}/events/{eventId}/odds")
                        .queryParam("apiKey", key)
                        .queryParam("regions", region)
                        .queryParam("markets", "h2h,totals")
                        .queryParam("oddsFormat", "decimal")
                        .build(sportKey, eventId))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getOddsForEvent(String eventId) {
        return getOddsForEvent("soccer_epl", eventId, "uk");
    }

    // ── Events ────────────────────────────────────────────────────

    public JsonNode getEvents(String sportKey) {
        log.info("📋 Fetching events for sport: {}", sportKey);
        return executeWithFallback((client, key) -> client.get()
                .uri(u -> u.path("/sports/{sport}/events")
                        .queryParam("apiKey", key)
                        .build(sportKey))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    // ── Scores ────────────────────────────────────────────────────

    public JsonNode getScores(String sportKey, int daysFrom) {
        log.info("🏁 Fetching scores — sport: {}, daysFrom: {}", sportKey, daysFrom);
        return executeWithFallback((client, key) -> client.get()
                .uri(u -> {
                    var builder = u.path("/sports/{sport}/scores")
                            .queryParam("apiKey", key);
                    if (daysFrom > 0) builder.queryParam("daysFrom", daysFrom);
                    return builder.build(sportKey);
                })
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public JsonNode getLiveScores(String sportKey) {
        return getScores(sportKey, 0);
    }

    // ── Event Markets ─────────────────────────────────────────────

    public JsonNode getEventMarkets(String sportKey, String eventId, String region) {
        log.info("🛒 Fetching markets — event: {}, region: {}", eventId, region);
        return executeWithFallback((client, key) -> client.get()
                .uri(u -> u.path("/sports/{sport}/events/{eventId}/markets")
                        .queryParam("apiKey", key)
                        .queryParam("regions", region)
                        .build(sportKey, eventId))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }
}