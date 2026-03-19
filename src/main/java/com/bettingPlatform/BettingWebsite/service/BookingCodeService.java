package com.bettingPlatform.BettingWebsite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * BookingCodeService
 *
 * ── Workflow ──────────────────────────────────────────────────────────────────
 *  1. Admin enters a booking code (e.g. "C76V4U") + selects bookmaker
 *  2. Service calls the bookmaker's public share API
 *  3. Bookmaker returns JSON with every game in that slip
 *  4. Service parses the JSON → SlipSelection list (teams, market, pick, odds, kickoff, league)
 *  5. Returns BookingCodeResult — ready to use in controller (preview or create slip)
 *
 * ── Anti-bot / Cloudflare bypass ─────────────────────────────────────────────
 *  - Full Chrome-like User-Agent
 *  - sec-ch-ua + sec-fetch-* headers
 *  - Referer + Origin matching the bookmaker domain
 *  - Small randomised delay between requests
 *  - Proper connect + read timeouts
 *
 * ── Supported bookmakers ──────────────────────────────────────────────────────
 *  - sportybet-gh  → GET https://www.sportybet.com/api/gh/orders/share/{CODE}
 *  - sportybet-ng  → GET https://www.sportybet.com/api/ng/orders/share/{CODE}
 *  - betway-gh     → GET https://www.betway.com.gh/api/Betslip/GetShareBetslip?shareCode={CODE}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingCodeService {

    // ── Timeouts ───────────────────────────────────────────────────────────────
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 15_000;

    // ── Base URLs ──────────────────────────────────────────────────────────────
    private static final String SPORTYBET_GH_URL = "https://www.sportybet.com/api/gh/orders/share/";
    private static final String SPORTYBET_NG_URL = "https://www.sportybet.com/api/ng/orders/share/";
    private static final String BETWAY_GH_URL    = "https://www.betway.com.gh/api/Betslip/GetShareBetslip?shareCode=";

    // ── Referer / Origin pairs ─────────────────────────────────────────────────
    private static final String REFERER_SPORTYBET = "https://www.sportybet.com/gh/";
    private static final String ORIGIN_SPORTYBET  = "https://www.sportybet.com";
    private static final String REFERER_BETWAY    = "https://www.betway.com.gh/";
    private static final String ORIGIN_BETWAY     = "https://www.betway.com.gh";

    // ── Browser-like headers ───────────────────────────────────────────────────
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36";

    private static final String ACCEPT          = "application/json, text/plain, */*";
    private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";
    private static final String ACCEPT_ENCODING = "gzip, deflate, br";

    // ═══════════════════════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A single game selection from inside the booking code slip.
     * All fields come directly from the bookmaker API response.
     */
    public record SlipSelection(
            String homeTeam,    // e.g. "Arsenal"
            String awayTeam,    // e.g. "Chelsea"
            String market,      // e.g. "1X2", "Both Teams to Score"
            String outcome,     // e.g. "1", "Yes", "Over 2.5"
            double odds,        // e.g. 2.10
            String kickoffTime, // e.g. "2025-06-01T15:00:00"
            String league       // e.g. "Premier League"
    ) {}

    /**
     * Full result returned after fetching and parsing a booking code.
     * Contains every game in the slip with all its details.
     */
    public record BookingCodeResult(
            String bookmaker,               // e.g. "Sportybet Ghana"
            String bookingCode,             // e.g. "C76V4U"
            double totalOdds,               // combined odds across all selections
            int totalSelections,            // number of games in the slip
            List<SlipSelection> selections, // all parsed games with details
            String rawJson                  // raw API response (for storage/debugging)
    ) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Main entry point — auto-routes to the correct bookmaker.
     *
     * @param bookingCode  e.g. "C76V4U"
     * @param bookmaker    "sportybet-gh" | "sportybet-ng" | "betway-gh"
     * @return BookingCodeResult with all games and their details from the slip
     */
    public BookingCodeResult fetch(String bookingCode, String bookmaker) {
        return switch (bookmaker.toLowerCase().trim()) {
            case "sportybet-gh", "sportybet_gh", "sportybet ghana"   -> fetchSportybetGhana(bookingCode);
            case "sportybet-ng", "sportybet_ng", "sportybet nigeria" -> fetchSportybetNigeria(bookingCode);
            case "betway-gh",   "betway_gh",    "betway ghana"      -> fetchBetwayGhana(bookingCode);
            default -> throw new RuntimeException(
                    "Unsupported bookmaker: '" + bookmaker + "'. " +
                            "Accepted values: sportybet-gh | sportybet-ng | betway-gh");
        };
    }

    // ── Per-bookmaker fetchers ─────────────────────────────────────────────────

    public BookingCodeResult fetchSportybetGhana(String bookingCode) {
        String code = bookingCode.trim().toUpperCase();
        String url  = SPORTYBET_GH_URL + code;
        log.info("📡 [Sportybet GH] Fetching code {} → {}", code, url);
        String raw = doGet(url, REFERER_SPORTYBET, ORIGIN_SPORTYBET);
        printRaw("Sportybet GH", code, raw);
        return parseSportybet(raw, code, "Sportybet Ghana");
    }

    public BookingCodeResult fetchSportybetNigeria(String bookingCode) {
        String code = bookingCode.trim().toUpperCase();
        String url  = SPORTYBET_NG_URL + code;
        log.info("📡 [Sportybet NG] Fetching code {} → {}", code, url);
        String raw = doGet(url, REFERER_SPORTYBET, ORIGIN_SPORTYBET);
        printRaw("Sportybet NG", code, raw);
        return parseSportybet(raw, code, "Sportybet Nigeria");
    }

    public BookingCodeResult fetchBetwayGhana(String bookingCode) {
        String code = bookingCode.trim().toUpperCase();
        String url  = BETWAY_GH_URL + code;
        log.info("📡 [Betway GH] Fetching code {} → {}", code, url);
        String raw = doGet(url, REFERER_BETWAY, ORIGIN_BETWAY);
        printRaw("Betway GH", code, raw);
        return parseBetway(raw, code);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HTTP — full browser headers to get past Cloudflare
    // ═══════════════════════════════════════════════════════════════════════════

    private String doGet(String url, String referer, String origin) {

        // 1. Timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        RestTemplate rest = new RestTemplate(factory);

        // 2. Full browser-like headers
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT,      USER_AGENT);
        headers.set(HttpHeaders.ACCEPT,          ACCEPT);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE);
        headers.set(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING);
        headers.set(HttpHeaders.REFERER,         referer);
        headers.set("Origin",                    origin);
        headers.set("Connection",                "keep-alive");
        headers.set("Cache-Control",             "no-cache");
        headers.set("Pragma",                    "no-cache");

        // 3. sec-fetch headers — these are what Cloudflare checks most
        headers.set("sec-ch-ua",
                "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"");
        headers.set("sec-ch-ua-mobile",   "?0");
        headers.set("sec-ch-ua-platform", "\"Windows\"");
        headers.set("Sec-Fetch-Dest",     "empty");
        headers.set("Sec-Fetch-Mode",     "cors");
        headers.set("Sec-Fetch-Site",     "same-origin");

        // 4. Small random delay — avoids rate limiting
        try {
            Thread.sleep(300 + (long) (Math.random() * 400));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // 5. Fire request
        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response =
                    rest.exchange(url, HttpMethod.GET, entity, String.class);

            log.info("✅ HTTP {} — {} chars received",
                    response.getStatusCode(),
                    response.getBody() != null ? response.getBody().length() : 0);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Non-2xx response from bookmaker: " + response.getStatusCode());
            }

            return response.getBody();

        } catch (HttpClientErrorException e) {
            // 4xx — bad code, expired, wrong region etc.
            log.error("❌ Client error {} for {}: {}", e.getStatusCode(), url, e.getResponseBodyAsString());
            throw new RuntimeException(
                    "Bookmaker rejected the request (HTTP " + e.getStatusCode() + "). " +
                            "The code may be invalid, expired, or wrong region. " +
                            "Response: " + e.getResponseBodyAsString(), e);

        } catch (HttpServerErrorException e) {
            // 5xx — bookmaker side issue
            log.error("❌ Server error {} for {}: {}", e.getStatusCode(), url, e.getResponseBodyAsString());
            throw new RuntimeException(
                    "Bookmaker API returned a server error (HTTP " + e.getStatusCode() + "). " +
                            "Please try again shortly.", e);

        } catch (Exception e) {
            log.error("❌ Request failed for {}: {}", url, e.getMessage());
            throw new RuntimeException(
                    "Could not reach bookmaker API. Cause: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PARSERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse Sportybet JSON.
     *
     * {
     *   "businessCode": 0,
     *   "data": {
     *     "totalOdds": "25.50",
     *     "outcomes": [
     *       {
     *         "homeTeamName":   "Arsenal",
     *         "awayTeamName":   "Chelsea",
     *         "marketName":     "1X2",
     *         "outcomeName":    "1",
     *         "odds":           "2.10",
     *         "startTime":      "2025-06-01T15:00:00",
     *         "tournamentName": "Premier League"
     *       }
     *     ]
     *   }
     * }
     */
    private BookingCodeResult parseSportybet(String raw, String bookingCode, String bookmakerName) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(raw);

            // businessCode 0 = success, anything else = error
            int businessCode = root.path("businessCode").asInt(-1);
            if (businessCode != 0) {
                String msg = root.path("message").asText("Unknown Sportybet error");
                throw new RuntimeException(
                        "Sportybet API returned error code " + businessCode + ": " + msg);
            }

            JsonNode data      = root.path("data");
            double   totalOdds = data.path("totalOdds").asDouble(0.0);
            JsonNode outcomes  = data.path("outcomes");

            if (!outcomes.isArray() || outcomes.isEmpty()) {
                throw new RuntimeException(
                        "No games found in Sportybet slip for code: " + bookingCode +
                                ". The code may be expired or invalid.");
            }

            List<SlipSelection> selections = new ArrayList<>();

            for (JsonNode o : outcomes) {
                SlipSelection sel = new SlipSelection(
                        o.path("homeTeamName").asText("Unknown"),
                        o.path("awayTeamName").asText("Unknown"),
                        o.path("marketName").asText("Unknown"),
                        o.path("outcomeName").asText("Unknown"),
                        o.path("odds").asDouble(0.0),
                        o.path("startTime").asText("N/A"),
                        o.path("tournamentName").asText("Unknown")
                );
                selections.add(sel);

                log.info("  🎯 [{}] {} vs {} | {} → {} @ {} | Kickoff: {}",
                        sel.league(), sel.homeTeam(), sel.awayTeam(),
                        sel.market(), sel.outcome(), sel.odds(), sel.kickoffTime());
            }

            BookingCodeResult result = new BookingCodeResult(
                    bookmakerName, bookingCode, totalOdds, selections.size(), selections, raw);

            printResult(result);
            return result;

        } catch (RuntimeException e) {
            throw e; // re-throw known errors unchanged
        } catch (Exception e) {
            log.error("❌ Sportybet parse error: {}", e.getMessage());
            throw new RuntimeException("Failed to parse Sportybet response: " + e.getMessage(), e);
        }
    }

    /**
     * Parse Betway Ghana JSON.
     *
     * {
     *   "IsSuccess": true,
     *   "Betslip": {
     *     "TotalOdds": 15.75,
     *     "Selections": [
     *       {
     *         "HomeTeamName":    "Hearts of Oak",
     *         "AwayTeamName":    "Kotoko",
     *         "MarketName":      "Match Result",
     *         "OutcomeName":     "Home",
     *         "Price":           1.85,
     *         "KickOffDate":     "2025-06-01T15:00:00",
     *         "CompetitionName": "Ghana Premier League"
     *       }
     *     ]
     *   }
     * }
     */
    private BookingCodeResult parseBetway(String raw, String bookingCode) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(raw);

            boolean isSuccess = root.path("IsSuccess").asBoolean(false);
            if (!isSuccess) {
                String msg = root.path("Message").asText("Unknown Betway error");
                throw new RuntimeException("Betway API error: " + msg);
            }

            JsonNode betslip   = root.path("Betslip");
            double   totalOdds = betslip.path("TotalOdds").asDouble(0.0);
            JsonNode sels      = betslip.path("Selections");

            if (!sels.isArray() || sels.isEmpty()) {
                throw new RuntimeException(
                        "No games found in Betway slip for code: " + bookingCode +
                                ". The code may be expired or invalid.");
            }

            List<SlipSelection> selections = new ArrayList<>();

            for (JsonNode s : sels) {
                SlipSelection sel = new SlipSelection(
                        s.path("HomeTeamName").asText("Unknown"),
                        s.path("AwayTeamName").asText("Unknown"),
                        s.path("MarketName").asText("Unknown"),
                        s.path("OutcomeName").asText("Unknown"),
                        s.path("Price").asDouble(0.0),
                        s.path("KickOffDate").asText("N/A"),
                        s.path("CompetitionName").asText("Unknown")
                );
                selections.add(sel);

                log.info("  🎯 [{}] {} vs {} | {} → {} @ {} | Kickoff: {}",
                        sel.league(), sel.homeTeam(), sel.awayTeam(),
                        sel.market(), sel.outcome(), sel.odds(), sel.kickoffTime());
            }

            BookingCodeResult result = new BookingCodeResult(
                    "Betway Ghana", bookingCode, totalOdds, selections.size(), selections, raw);

            printResult(result);
            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Betway parse error: {}", e.getMessage());
            throw new RuntimeException("Failed to parse Betway response: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRINT HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void printRaw(String label, String code, String raw) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.printf ("📦 RAW RESPONSE — %s | Code: %s%n", label, code);
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println(raw);
        System.out.println("═══════════════════════════════════════════════════════════════");
        log.debug("📦 Raw response [{} / {}] — {} chars", label, code,
                raw != null ? raw.length() : 0);
    }

    private void printResult(BookingCodeResult result) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║          ✅  BOOKING CODE PARSED SUCCESSFULLY                    ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf ("║  Bookmaker  : %-50s ║%n", result.bookmaker());
        System.out.printf ("║  Code       : %-50s ║%n", result.bookingCode());
        System.out.printf ("║  Total Odds : %-50s ║%n", String.format("%.2f", result.totalOdds()));
        System.out.printf ("║  Games      : %-50s ║%n", result.totalSelections());
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  GAMES IN THIS SLIP:                                             ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");

        for (int i = 0; i < result.selections().size(); i++) {
            SlipSelection s = result.selections().get(i);
            System.out.printf("║  #%d  %s  vs  %s%n",       i + 1, s.homeTeam(), s.awayTeam());
            System.out.printf("║       🏆 League  : %s%n",  s.league());
            System.out.printf("║       📊 Market  : %s%n",  s.market());
            System.out.printf("║       ✅ Pick    : %s%n",  s.outcome());
            System.out.printf("║       💰 Odds    : %.2f%n",s.odds());
            System.out.printf("║       ⏰ Kickoff : %s%n",  s.kickoffTime());
            if (i < result.selections().size() - 1) {
                System.out.println("║       ── ── ── ── ── ── ── ── ── ── ── ── ── ── ── ── ── ──  ║");
            }
        }

        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();

        log.info("✅ Done — {} | Code: {} | Odds: {} | {} games",
                result.bookmaker(), result.bookingCode(),
                result.totalOdds(), result.totalSelections());
    }
}