package com.bettingPlatform.BettingWebsite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * BookingCodeService
 *
 * Fetches and parses betting slip details from bookmaker share endpoints.
 * All configuration values are declared inline — no application.properties needed.
 *
 * Supported bookmakers:
 *  - Sportybet Ghana  → https://www.sportybet.com/api/gh/orders/share/{CODE}
 *  - Sportybet Nigeria → https://www.sportybet.com/api/ng/orders/share/{CODE}
 *  - Betway Ghana      → https://www.betway.com.gh/api/Betslip/GetShareBetslip?shareCode={CODE}
 */
@Service
@Slf4j
public class BookingCodeService {

    // ═══════════════════════════════════════════════════════════
    // ✅ ALL CONFIG VALUES — declared inline, no application.properties
    // ═══════════════════════════════════════════════════════════

    private static final String SPORTYBET_GH_BASE_URL =
            "https://www.sportybet.com/api/gh/orders/share/";

    private static final String SPORTYBET_NG_BASE_URL =
            "https://www.sportybet.com/api/ng/orders/share/";

    private static final String BETWAY_GH_BASE_URL =
            "https://www.betway.com.gh/api/Betslip/GetShareBetslip?shareCode=";

    /** Browser-like User-Agent so Sportybet/Betway don't reject the request */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36";

    private static final String ACCEPT_HEADER     = "application/json, text/plain, */*";
    private static final String ACCEPT_LANG       = "en-US,en;q=0.9";
    private static final String REFERER_SPORTYBET = "https://www.sportybet.com/";
    private static final String REFERER_BETWAY    = "https://www.betway.com.gh/";

    private static final int    CONNECT_TIMEOUT_MS = 8_000;
    private static final int    READ_TIMEOUT_MS    = 10_000;

    // ═══════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════

    public record SlipSelection(
            String homeTeam,
            String awayTeam,
            String market,
            String outcome,
            double odds,
            String kickoffTime,
            String league
    ) {}

    public record BookingCodeResult(
            String bookmaker,
            String bookingCode,
            double totalOdds,
            int totalSelections,
            List<SlipSelection> selections,
            String rawJson   // full raw response for debugging / storage
    ) {}

    // ═══════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch slip details from Sportybet Ghana.
     *
     * @param bookingCode e.g. "ABC123"
     * @return parsed BookingCodeResult
     */
    public BookingCodeResult fetchSportybetGhana(String bookingCode) {
        String url = SPORTYBET_GH_BASE_URL + bookingCode.trim().toUpperCase();
        log.info("📡 [Sportybet GH] Fetching booking code: {} → {}", bookingCode, url);
        String raw = doGet(url, REFERER_SPORTYBET);
        printRaw("Sportybet GH", bookingCode, raw);
        return parseSportybet(raw, bookingCode, "Sportybet Ghana");
    }

    /**
     * Fetch slip details from Sportybet Nigeria.
     *
     * @param bookingCode e.g. "ABC123"
     * @return parsed BookingCodeResult
     */
    public BookingCodeResult fetchSportybetNigeria(String bookingCode) {
        String url = SPORTYBET_NG_BASE_URL + bookingCode.trim().toUpperCase();
        log.info("📡 [Sportybet NG] Fetching booking code: {} → {}", bookingCode, url);
        String raw = doGet(url, REFERER_SPORTYBET);
        printRaw("Sportybet NG", bookingCode, raw);
        return parseSportybet(raw, bookingCode, "Sportybet Nigeria");
    }

    /**
     * Fetch slip details from Betway Ghana.
     *
     * @param bookingCode e.g. "ABC123"
     * @return parsed BookingCodeResult
     */
    public BookingCodeResult fetchBetwayGhana(String bookingCode) {
        String url = BETWAY_GH_BASE_URL + bookingCode.trim().toUpperCase();
        log.info("📡 [Betway GH] Fetching booking code: {} → {}", bookingCode, url);
        String raw = doGet(url, REFERER_BETWAY);
        printRaw("Betway GH", bookingCode, raw);
        return parseBetway(raw, bookingCode);
    }

    /**
     * Auto-detect bookmaker from code prefix and region hint, then fetch.
     *
     * @param bookingCode  the raw code
     * @param bookmaker    "sportybet-gh" | "sportybet-ng" | "betway-gh"
     */
    public BookingCodeResult fetch(String bookingCode, String bookmaker) {
        return switch (bookmaker.toLowerCase().trim()) {
            case "sportybet-gh", "sportybet_gh", "sportybet ghana" -> fetchSportybetGhana(bookingCode);
            case "sportybet-ng", "sportybet_ng", "sportybet nigeria" -> fetchSportybetNigeria(bookingCode);
            case "betway-gh",  "betway_gh",  "betway ghana"  -> fetchBetwayGhana(bookingCode);
            default -> throw new RuntimeException(
                    "Unsupported bookmaker: '" + bookmaker + "'. " +
                    "Supported: sportybet-gh, sportybet-ng, betway-gh");
        };
    }

    // ═══════════════════════════════════════════════════════════
    // HTTP
    // ═══════════════════════════════════════════════════════════

    private String doGet(String url, String referer) {
        RestTemplate rest = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT,      USER_AGENT);
        headers.set(HttpHeaders.ACCEPT,          ACCEPT_HEADER);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANG);
        headers.set(HttpHeaders.REFERER,         referer);
        headers.set("Origin",                    referer.replaceAll("/$", ""));
        headers.set("Connection",                "keep-alive");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response =
                    rest.exchange(url, HttpMethod.GET, entity, String.class);

            log.info("✅ HTTP {} received ({} chars)", response.getStatusCode(),
                    response.getBody() != null ? response.getBody().length() : 0);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Non-200 response: " + response.getStatusCode());
            }
            return response.getBody();

        } catch (Exception e) {
            log.error("❌ HTTP request failed for {}: {}", url, e.getMessage());
            throw new RuntimeException("Failed to fetch booking code from: " + url, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PARSERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Parse Sportybet JSON response.
     *
     * Sportybet response shape (simplified):
     * {
     *   "businessCode": 0,
     *   "data": {
     *     "orderNo": "...",
     *     "totalOdds": "25.50",
     *     "outcomes": [
     *       {
     *         "homeTeamName": "Arsenal",
     *         "awayTeamName": "Chelsea",
     *         "marketName": "1X2",
     *         "outcomeName": "1",
     *         "odds": "2.10",
     *         "startTime": "2025-06-01T15:00:00",
     *         "tournamentName": "Premier League"
     *       },
     *       ...
     *     ]
     *   }
     * }
     */
    private BookingCodeResult parseSportybet(String raw, String bookingCode, String bookmakerName) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(raw);

            // Check business code — 0 means success
            int businessCode = root.path("businessCode").asInt(-1);
            if (businessCode != 0) {
                String msg = root.path("message").asText("Unknown error");
                throw new RuntimeException("Sportybet API error (code " + businessCode + "): " + msg);
            }

            JsonNode data = root.path("data");
            double totalOdds = data.path("totalOdds").asDouble(0.0);

            List<SlipSelection> selections = new ArrayList<>();
            JsonNode outcomes = data.path("outcomes");

            if (outcomes.isArray()) {
                for (JsonNode o : outcomes) {
                    SlipSelection sel = new SlipSelection(
                            o.path("homeTeamName").asText(""),
                            o.path("awayTeamName").asText(""),
                            o.path("marketName").asText(""),
                            o.path("outcomeName").asText(""),
                            o.path("odds").asDouble(0.0),
                            o.path("startTime").asText(""),
                            o.path("tournamentName").asText("")
                    );
                    selections.add(sel);

                    // ✅ Print each selection to console
                    System.out.printf(
                            "  🎯 [%s] %s vs %s | Market: %s | Pick: %s | Odds: %.2f | Kickoff: %s%n",
                            sel.league(), sel.homeTeam(), sel.awayTeam(),
                            sel.market(), sel.outcome(), sel.odds(), sel.kickoffTime()
                    );
                }
            }

            BookingCodeResult result = new BookingCodeResult(
                    bookmakerName, bookingCode, totalOdds, selections.size(), selections, raw
            );

            printResult(result);
            return result;

        } catch (Exception e) {
            log.error("❌ Failed to parse Sportybet response: {}", e.getMessage());
            throw new RuntimeException("Sportybet parse error: " + e.getMessage(), e);
        }
    }

    /**
     * Parse Betway Ghana JSON response.
     *
     * Betway response shape (simplified):
     * {
     *   "IsSuccess": true,
     *   "Betslip": {
     *     "TotalOdds": 15.75,
     *     "Selections": [
     *       {
     *         "HomeTeamName": "Hearts of Oak",
     *         "AwayTeamName": "Kotoko",
     *         "MarketName": "Match Result",
     *         "OutcomeName": "Home",
     *         "Price": 1.85,
     *         "KickOffDate": "2025-06-01T15:00:00",
     *         "CompetitionName": "Ghana Premier League"
     *       },
     *       ...
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
                String msg = root.path("Message").asText("Unknown error");
                throw new RuntimeException("Betway API error: " + msg);
            }

            JsonNode betslip   = root.path("Betslip");
            double totalOdds   = betslip.path("TotalOdds").asDouble(0.0);

            List<SlipSelection> selections = new ArrayList<>();
            JsonNode sels = betslip.path("Selections");

            if (sels.isArray()) {
                for (JsonNode s : sels) {
                    SlipSelection sel = new SlipSelection(
                            s.path("HomeTeamName").asText(""),
                            s.path("AwayTeamName").asText(""),
                            s.path("MarketName").asText(""),
                            s.path("OutcomeName").asText(""),
                            s.path("Price").asDouble(0.0),
                            s.path("KickOffDate").asText(""),
                            s.path("CompetitionName").asText("")
                    );
                    selections.add(sel);

                    // ✅ Print each selection to console
                    System.out.printf(
                            "  🎯 [%s] %s vs %s | Market: %s | Pick: %s | Odds: %.2f | Kickoff: %s%n",
                            sel.league(), sel.homeTeam(), sel.awayTeam(),
                            sel.market(), sel.outcome(), sel.odds(), sel.kickoffTime()
                    );
                }
            }

            BookingCodeResult result = new BookingCodeResult(
                    "Betway Ghana", bookingCode, totalOdds, selections.size(), selections, raw
            );

            printResult(result);
            return result;

        } catch (Exception e) {
            log.error("❌ Failed to parse Betway response: {}", e.getMessage());
            throw new RuntimeException("Betway parse error: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PRINT HELPERS — all go to console & log
    // ═══════════════════════════════════════════════════════════

    private void printRaw(String label, String code, String raw) {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.printf("📦 RAW RESPONSE — %s | Code: %s%n", label, code);
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println(raw);
        System.out.println("═══════════════════════════════════════════════════════");
        log.debug("📦 Raw response for {} [{}]: {} chars", label, code,
                raw != null ? raw.length() : 0);
    }

    private void printResult(BookingCodeResult result) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.printf ("║  ✅ BOOKING CODE PARSED SUCCESSFULLY                 ║%n");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.printf ("║  Bookmaker  : %-38s ║%n", result.bookmaker());
        System.out.printf ("║  Code       : %-38s ║%n", result.bookingCode());
        System.out.printf ("║  Total Odds : %-38s ║%n", String.format("%.2f", result.totalOdds()));
        System.out.printf ("║  Selections : %-38s ║%n", result.totalSelections());
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  SELECTIONS:                                         ║");
        for (int i = 0; i < result.selections().size(); i++) {
            SlipSelection s = result.selections().get(i);
            System.out.printf("║  %d. %s vs %s%n", i + 1, s.homeTeam(), s.awayTeam());
            System.out.printf("║     League: %s%n", s.league());
            System.out.printf("║     Market: %s | Pick: %s | Odds: %.2f%n",
                    s.market(), s.outcome(), s.odds());
            System.out.printf("║     Kickoff: %s%n", s.kickoffTime());
        }
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        log.info("✅ Parsed {} — Code: {} | Odds: {} | {} selections",
                result.bookmaker(), result.bookingCode(),
                result.totalOdds(), result.totalSelections());
    }
}