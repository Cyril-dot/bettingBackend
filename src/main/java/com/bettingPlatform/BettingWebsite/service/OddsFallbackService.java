package com.bettingPlatform.BettingWebsite.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OddsFallbackService {

    private final OddsApiClient oddsApiClient;

    /**
     * Call this everywhere you need odds for a match.
     *
     * Pass the odds you already have from your primary source.
     * If any are null, it will retry via OddsApiClient (which internally
     * round-robins across up to 3 API keys before giving up).
     *
     * @param primaryHome  home odds from primary source (nullable)
     * @param primaryDraw  draw odds from primary source (nullable)
     * @param primaryAway  away odds from primary source (nullable)
     * @param sportKey     e.g. "soccer_epl", "soccer_uefa_champs_league"
     * @param eventId      The Odds API event ID for this match
     * @param region       e.g. "uk", "eu", "us"
     */
    public OddsResult getOdds(Double primaryHome, Double primaryDraw, Double primaryAway,
                              String sportKey, String eventId, String region) {

        // ── Primary odds present → return as-is ──────────────────
        if (primaryHome != null && primaryDraw != null && primaryAway != null) {
            log.debug("✅ Using primary odds for event {}", eventId);
            return new OddsResult(primaryHome, primaryDraw, primaryAway, false);
        }

        log.warn("⚠️ Missing primary odds for event {}. Trying OddsApi fallback.", eventId);

        // ── Fallback: fetch event odds via OddsApiClient ──────────
        // OddsApiClient already handles key1 → key2 → key3 rotation internally
        JsonNode response = oddsApiClient.getOddsForEvent(sportKey, eventId, region);
        OddsResult result = extractH2HOdds(response, eventId);
        if (result != null) return result;

        // ── Nothing found ─────────────────────────────────────────
        log.warn("❌ No fallback odds found for event {}. Returning nulls.", eventId);
        return new OddsResult(null, null, null, false);
    }

    // Convenience overload — defaults to EPL / UK region
    public OddsResult getOdds(Double primaryHome, Double primaryDraw, Double primaryAway, String eventId) {
        return getOdds(primaryHome, primaryDraw, primaryAway, "soccer_epl", eventId, "uk");
    }

    // ── Parser ────────────────────────────────────────────────────

    /**
     * Extracts Home / Draw / Away odds from The Odds API response.
     *
     * The Odds API h2h market structure:
     * [ { bookmakers: [ { markets: [ { key: "h2h", outcomes: [
     *     { name: "Home Team", price: 1.9 },
     *     { name: "Draw",      price: 3.5 },
     *     { name: "Away Team", price: 4.0 }
     * ] } ] } ] } ]
     */
    private OddsResult extractH2HOdds(JsonNode root, String eventId) {
        try {
            // Root is an array of events, or a single event object
            JsonNode event = root.isArray() ? root.get(0) : root;
            if (event == null || event.isMissingNode()) return null;

            String homeTeam = event.path("home_team").asText("");
            String awayTeam = event.path("away_team").asText("");

            JsonNode bookmakers = event.path("bookmakers");
            if (bookmakers.isMissingNode() || bookmakers.isEmpty()) return null;

            for (JsonNode bookmaker : bookmakers) {
                for (JsonNode market : bookmaker.path("markets")) {
                    if (!"h2h".equals(market.path("key").asText())) continue;

                    Double home = null, draw = null, away = null;

                    for (JsonNode outcome : market.path("outcomes")) {
                        String name = outcome.path("name").asText("");
                        double price = outcome.path("price").asDouble(0);
                        if (name.equalsIgnoreCase(homeTeam))  home = price;
                        else if (name.equalsIgnoreCase("Draw")) draw = price;
                        else if (name.equalsIgnoreCase(awayTeam)) away = price;
                    }

                    if (home != null && draw != null && away != null) {
                        log.info("✅ Fallback odds found for event {} — H:{} D:{} A:{}", eventId, home, draw, away);
                        return new OddsResult(home, draw, away, true);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract odds from OddsApi response for event {}: {}", eventId, e.getMessage());
        }
        return null;
    }

    // ── Result record ─────────────────────────────────────────────

    public record OddsResult(
            Double home,
            Double draw,
            Double away,
            boolean isFallback  // true = came from OddsApi fallback, false = primary source
    ) {
        public boolean hasOdds() {
            return home != null && draw != null && away != null;
        }
    }
}