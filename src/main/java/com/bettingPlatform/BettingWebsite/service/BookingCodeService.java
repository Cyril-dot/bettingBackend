package com.bettingPlatform.BettingWebsite.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BookingCodeService
 *
 * ── Workflow ──────────────────────────────────────────────────────────────────
 *  1. Admin enters booking code + bookmaker
 *  2. Route through ScrapeOps residential proxy (rotates across 4 API keys)
 *       GET https://proxy.scrapeops.io/v1/
 *           ?api_key={KEY}
 *           &url=https://www.sportybet.com/api/gh/orders/share/{CODE}
 *           &residential=true
 *           &country=gh
 *  3. Sportybet returns XML — parse every field
 *  4. Return BookingCodeResult with full game details
 *
 * ── 4 Rotating ScrapeOps Keys ────────────────────────────────────────────────
 *  Each key has 1,000 free credits/month (resets every 30 days).
 *  Keys rotate automatically — if one fails or is exhausted the next is used.
 *  Total: ~4,000 free requests/month across all keys.
 *  Sign up free at: https://scrapeops.io
 *
 * ── Fields extracted from Sportybet XML ──────────────────────────────────────
 *  homeTeamName, awayTeamName, league, country, market, outcome,
 *  odds, kickoffTime, matchStatus, score, playedSeconds, bookingStatus
 */
@Service
@Slf4j
public class BookingCodeService {

    // ── 4 ScrapeOps API keys — rotate to spread the 1,000 req/month limit ─────
    // Create 4 free accounts at https://scrapeops.io and paste keys here
    private static final String[] SCRAPEOPS_KEYS = {
            "230b20f9-cc9f-4edb-bfee-38430ce0d22d",  // Key 1 — primary
            "c2e7161e-cd7e-4505-8ab7-94ff993bf23c",                      // Key 2 — fallback
            "fca93b28-accd-4946-ab33-07d1f43ffb1d",                      // Key 3 — fallback
            "7cb32829-319f-4de6-8c37-162a90d729af",                      // Key 4 — fallback
    };

    private static final String SCRAPEOPS_PROXY_URL = "https://proxy.scrapeops.io/v1/";

    // Round-robin key index (thread-safe)
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    // ── Timeouts ───────────────────────────────────────────────────────────────
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS    = 30_000;

    // ── Sportybet base URLs ────────────────────────────────────────────────────
    private static final String SPORTYBET_GH_URL = "https://www.sportybet.com/api/gh/orders/share/";
    private static final String SPORTYBET_NG_URL = "https://www.sportybet.com/api/ng/orders/share/";
    private static final String BETWAY_GH_URL    = "https://www.betway.com.gh/api/Betslip/GetShareBetslip?shareCode=";

    // ── Kickoff time formatter ─────────────────────────────────────────────────
    private static final DateTimeFormatter KICKOFF_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    // ═══════════════════════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Full details of a single game selection inside the booking code slip.
     * All fields extracted directly from the Sportybet XML response.
     */
    public record SlipSelection(
            // ── Teams ──────────────────────────────────────────────────────────
            String homeTeam,        // e.g. "Club Necaxa"
            String awayTeam,        // e.g. "Club Tijuana de Caliente"

            // ── League / Competition ───────────────────────────────────────────
            String league,          // e.g. "Liga MX, Clausura"
            String country,         // e.g. "Mexico"
            String sport,           // e.g. "Football"

            // ── Bet details ────────────────────────────────────────────────────
            String market,          // e.g. "1X2"
            String outcome,         // e.g. "Home"
            double odds,            // e.g. 2.20

            // ── Match timing ──────────────────────────────────────────────────
            String kickoffTime,     // e.g. "2026-03-21 01:00 UTC"
            long   kickoffTimestamp, // unix ms e.g. 1774054800000

            // ── Match state ───────────────────────────────────────────────────
            String matchStatus,     // e.g. "Not start" | "Ended" | "Live"
            String score,           // e.g. "4:0" (empty if not started)
            String playedTime,      // e.g. "90:00" (empty if not started)
            int    statusCode,      // 0=not started, 1=live, 4=ended

            // ── Booking state ─────────────────────────────────────────────────
            String bookingStatus,   // e.g. "Booked"
            boolean isWinning,      // true if this selection won

            // ── IDs (for cross-referencing) ───────────────────────────────────
            String eventId,         // e.g. "sr:match:66856082"
            String gameId           // e.g. "20199"
    ) {}

    /**
     * Full result returned after fetching and parsing a booking code.
     */
    public record BookingCodeResult(
            String bookmaker,               // e.g. "Sportybet Ghana"
            String bookingCode,             // e.g. "8RF5L8"
            double totalOdds,               // combined odds (product of all individual odds)
            int    totalSelections,         // number of games in the slip
            String deadline,                // e.g. "2026-03-31T01:00:00.000+00:00"
            List<SlipSelection> selections, // all games with full details
            String rawXml                   // full raw XML (for debugging/storage)
    ) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Main entry point — routes to the correct bookmaker.
     *
     * @param bookingCode  e.g. "8RF5L8"
     * @param bookmaker    "sportybet-gh" | "sportybet-ng" | "betway-gh"
     */
    public BookingCodeResult fetch(String bookingCode, String bookmaker) {
        String code = bookingCode.trim().toUpperCase();
        return switch (bookmaker.toLowerCase().trim()) {
            case "sportybet-gh", "sportybet_gh", "sportybet ghana"   -> fetchSportybetGhana(code);
            case "sportybet-ng", "sportybet_ng", "sportybet nigeria" -> fetchSportybetNigeria(code);
            case "betway-gh",    "betway_gh",    "betway ghana"      -> fetchBetwayGhana(code);
            default -> throw new RuntimeException(
                    "Unsupported bookmaker: '" + bookmaker + "'. " +
                            "Accepted: sportybet-gh | sportybet-ng | betway-gh");
        };
    }

    public BookingCodeResult fetchSportybetGhana(String code) {
        log.info("📡 [Sportybet GH] Fetching code: {}", code);
        String xml = fetchViaProxy(SPORTYBET_GH_URL + code, "gh");
        return parseSportybetXml(xml, code, "Sportybet Ghana");
    }

    public BookingCodeResult fetchSportybetNigeria(String code) {
        log.info("📡 [Sportybet NG] Fetching code: {}", code);
        String xml = fetchViaProxy(SPORTYBET_NG_URL + code, "ng");
        return parseSportybetXml(xml, code, "Sportybet Nigeria");
    }

    public BookingCodeResult fetchBetwayGhana(String code) {
        log.info("📡 [Betway GH] Fetching code: {}", code);
        String raw = fetchViaProxy(BETWAY_GH_URL + code, "gh");
        return parseBetwayJson(raw, code);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCRAPEOPS PROXY — rotates across 4 keys
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Routes the target URL through ScrapeOps residential proxy.
     * Tries each API key in round-robin order — if one fails, moves to the next.
     *
     * @param targetUrl  the actual Sportybet/Betway URL to fetch
     * @param country    ISO country code for geo-targeting e.g. "gh", "ng"
     */
    private String fetchViaProxy(String targetUrl, String country) {
        int startIdx = keyIndex.get();
        int attempts = SCRAPEOPS_KEYS.length;

        for (int i = 0; i < attempts; i++) {
            int idx = (startIdx + i) % SCRAPEOPS_KEYS.length;
            String key = SCRAPEOPS_KEYS[idx];

            // Skip placeholder keys
            if (key.startsWith("REPLACE_WITH_KEY")) {
                log.debug("  ⏭ Skipping placeholder key #{}", idx + 1);
                continue;
            }

            try {
                log.info("  ▶ Trying ScrapeOps key #{} (index {})", idx + 1, idx);
                String result = doScrapeOpsGet(key, targetUrl, country);

                // Rotate to next key for next call (spread usage evenly)
                keyIndex.set((idx + 1) % SCRAPEOPS_KEYS.length);

                log.info("  ✅ Key #{} succeeded", idx + 1);
                return result;

            } catch (Exception e) {
                log.warn("  ⚠ Key #{} failed: {} — trying next key", idx + 1, e.getMessage());
            }
        }

        throw new RuntimeException(
                "All ScrapeOps keys failed for URL: " + targetUrl +
                        ". Check your API keys and usage limits at scrapeops.io");
    }

    private String doScrapeOpsGet(String apiKey, String targetUrl, String country) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        RestTemplate rest = new RestTemplate(factory);

        // Build ScrapeOps proxy URL
        String proxyUrl = SCRAPEOPS_PROXY_URL
                + "?api_key="     + apiKey
                + "&url="         + java.net.URLEncoder.encode(targetUrl, java.nio.charset.StandardCharsets.UTF_8)
                + "&residential=" + "true"
                + "&country="     + country;

        log.info("  📡 Proxy URL: {}...{}", proxyUrl.substring(0, 50), targetUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, "application/xml, text/xml, */*");

        try {
            ResponseEntity<String> response =
                    rest.exchange(proxyUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            String body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new RuntimeException("Non-2xx from ScrapeOps: " + response.getStatusCode());
            }

            log.info("  ✅ HTTP {} — {} chars", response.getStatusCode(), body.length());
            return body;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 403) {
                throw new RuntimeException("ScrapeOps key rejected (403) — key may be invalid or exhausted");
            }
            if (e.getStatusCode().value() == 429) {
                throw new RuntimeException("ScrapeOps rate limit (429) — too many requests on this key");
            }
            throw new RuntimeException("HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPORTYBET XML PARSER
    //
    // Parses the full XML structure:
    // <BaseRsp>
    //   <data>
    //     <shareCode>, <deadline>
    //     <outcomes>
    //       <outcomes>  ← one per game
    //         <homeTeamName>, <awayTeamName>
    //         <estimateStartTime>  ← unix ms timestamp
    //         <matchStatus>, <status>, <setScore>, <playedSeconds>
    //         <sport><name><category><name><tournament><name>
    //         <markets><markets>
    //           <desc>  ← market name e.g. "1X2"
    //           <outcomes><outcomes>
    //             <desc>   ← pick e.g. "Home"
    //             <odds>   ← e.g. 2.20
    //             <isWinning> ← 1 if won
    //         <bookingStatus>
    //         <eventId>, <gameId>
    // ═══════════════════════════════════════════════════════════════════════════

    private BookingCodeResult parseSportybetXml(String rawXml, String bookingCode, String bookmakerName) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(rawXml)));
            doc.getDocumentElement().normalize();

            // ── Check bizCode ──────────────────────────────────────────────────
            String bizCode = getTagValue(doc, "bizCode");
            String message = getTagValue(doc, "message");
            if (!"10000".equals(bizCode)) {
                throw new RuntimeException(
                        "Sportybet error (bizCode=" + bizCode + "): " + message +
                                ". Code may be invalid or expired.");
            }

            String shareCode = getTagValue(doc, "shareCode");
            String deadline  = getTagValue(doc, "deadline");

            log.info("  📋 shareCode={} deadline={}", shareCode, deadline);

            // ── Parse each <outcomes> node ─────────────────────────────────────
            NodeList outcomesList = doc.getElementsByTagName("outcomes");
            List<SlipSelection> selections = new ArrayList<>();
            double totalOdds = 1.0;

            for (int i = 0; i < outcomesList.getLength(); i++) {
                Node node = outcomesList.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) node;

                // Only process the outer <outcomes> nodes (direct children of <data><outcomes>)
                // Skip nested ones inside <markets>
                String homeTeam = getChildValue(el, "homeTeamName");
                if (homeTeam.isEmpty()) continue; // skip non-game nodes

                // ── Basic game info ────────────────────────────────────────────
                String awayTeam      = getChildValue(el, "awayTeamName");
                String eventId       = getChildValue(el, "eventId");
                String gameId        = getChildValue(el, "gameId");
                String matchStatus   = getChildValue(el, "matchStatus");
                String score         = getChildValue(el, "setScore");
                String playedTime    = getChildValue(el, "playedSeconds");
                String bookingStatus = getChildValue(el, "bookingStatus");
                int    statusCode    = parseInt(getChildValue(el, "status"));

                // ── Kickoff time ───────────────────────────────────────────────
                long   kickoffTs  = parseLong(getChildValue(el, "estimateStartTime"));
                String kickoffStr = kickoffTs > 0
                        ? KICKOFF_FMT.format(Instant.ofEpochMilli(kickoffTs))
                        : "TBD";

                // ── Sport / League / Country ───────────────────────────────────
                String sport   = "";
                String country = "";
                String league  = "";

                NodeList sportNodes = el.getElementsByTagName("sport");
                if (sportNodes.getLength() > 0) {
                    Element sportEl = (Element) sportNodes.item(0);
                    sport = getChildValue(sportEl, "name");

                    NodeList catNodes = sportEl.getElementsByTagName("category");
                    if (catNodes.getLength() > 0) {
                        Element catEl = (Element) catNodes.item(0);
                        country = getChildValue(catEl, "name");

                        NodeList tournNodes = catEl.getElementsByTagName("tournament");
                        if (tournNodes.getLength() > 0) {
                            Element tournEl = (Element) tournNodes.item(0);
                            league = getChildValue(tournEl, "name");
                        }
                    }
                }

                // ── Market + Pick + Odds ───────────────────────────────────────
                String  market    = "";
                String  outcome   = "";
                double  odds      = 0.0;
                boolean isWinning = false;

                NodeList marketsList = el.getElementsByTagName("markets");
                for (int m = 0; m < marketsList.getLength(); m++) {
                    Node mNode = marketsList.item(m);
                    if (mNode.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element mEl = (Element) mNode;

                    // Get market name from <desc>
                    String mDesc = getChildValue(mEl, "desc");
                    if (!mDesc.isEmpty()) {
                        market = mDesc; // e.g. "1X2"

                        // Get the selected outcome
                        NodeList outcomeNodes2 = mEl.getElementsByTagName("outcomes");
                        for (int o = 0; o < outcomeNodes2.getLength(); o++) {
                            Node oNode = outcomeNodes2.item(o);
                            if (oNode.getNodeType() != Node.ELEMENT_NODE) continue;
                            Element oEl = (Element) oNode;

                            String oDesc = getChildValue(oEl, "desc");
                            String oOdds = getChildValue(oEl, "odds");
                            String oWin  = getChildValue(oEl, "isWinning");

                            if (!oDesc.isEmpty() && !oOdds.isEmpty()) {
                                outcome   = oDesc;  // e.g. "Home"
                                odds      = parseDouble(oOdds);
                                isWinning = "1".equals(oWin);
                                break;
                            }
                        }
                        break; // use first market only (the selected one)
                    }
                }

                // ── Multiply into total odds ───────────────────────────────────
                if (odds > 0) totalOdds *= odds;

                SlipSelection sel = new SlipSelection(
                        homeTeam, awayTeam,
                        league, country, sport,
                        market, outcome, odds,
                        kickoffStr, kickoffTs,
                        matchStatus, score, playedTime, statusCode,
                        bookingStatus, isWinning,
                        eventId, gameId
                );
                selections.add(sel);

                // ── Console print per game ─────────────────────────────────────
                System.out.printf(
                        "  🎯 [%s | %s] %s vs %s%n" +
                                "       📊 %s → %s @ %.2f%n" +
                                "       ⏰ %s | Status: %s%s%s%n",
                        country, league, homeTeam, awayTeam,
                        market, outcome, odds,
                        kickoffStr, matchStatus,
                        score.isEmpty() ? "" : " | Score: " + score,
                        isWinning ? " ✅ WON" : ""
                );

                log.info("  🎯 [{}/{}] {} vs {} | {} → {} @ {} | {} | {}",
                        country, league, homeTeam, awayTeam,
                        market, outcome, odds, kickoffStr, matchStatus);
            }

            if (selections.isEmpty()) {
                throw new RuntimeException(
                        "No games found in slip for code: " + bookingCode +
                                ". The code may be expired.");
            }

            // Round total odds to 2dp
            totalOdds = Math.round(totalOdds * 100.0) / 100.0;

            BookingCodeResult result = new BookingCodeResult(
                    bookmakerName, bookingCode, totalOdds,
                    selections.size(), deadline, selections, rawXml);

            printResult(result);
            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ XML parse error: {}", e.getMessage());
            throw new RuntimeException("Failed to parse Sportybet XML: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BETWAY GHANA JSON PARSER (fallback)
    // ═══════════════════════════════════════════════════════════════════════════

    private BookingCodeResult parseBetwayJson(String raw, String bookingCode) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(raw);

            if (!root.path("IsSuccess").asBoolean(false)) {
                throw new RuntimeException("Betway error: " + root.path("Message").asText("Unknown"));
            }

            com.fasterxml.jackson.databind.JsonNode betslip = root.path("Betslip");
            double totalOdds = betslip.path("TotalOdds").asDouble(0.0);
            com.fasterxml.jackson.databind.JsonNode sels = betslip.path("Selections");

            List<SlipSelection> selections = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode s : sels) {
                long kickoffTs = 0;
                String kickoffStr = s.path("KickOffDate").asText("TBD");
                SlipSelection sel = new SlipSelection(
                        s.path("HomeTeamName").asText("?"),
                        s.path("AwayTeamName").asText("?"),
                        s.path("CompetitionName").asText("?"),
                        "", "Football",
                        s.path("MarketName").asText("1X2"),
                        s.path("OutcomeName").asText("?"),
                        s.path("Price").asDouble(0.0),
                        kickoffStr, kickoffTs,
                        "Unknown", "", "", 0,
                        "Booked", false,
                        "", ""
                );
                selections.add(sel);
            }

            BookingCodeResult result = new BookingCodeResult(
                    "Betway Ghana", bookingCode, totalOdds,
                    selections.size(), "", selections, raw);
            printResult(result);
            return result;

        } catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException("Betway parse error: " + e.getMessage(), e); }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // XML HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String getTagValue(Document doc, String tag) {
        NodeList list = doc.getElementsByTagName(tag);
        if (list.getLength() == 0) return "";
        Node node = list.item(0);
        return node.getTextContent() != null ? node.getTextContent().trim() : "";
    }

    private String getChildValue(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return "";
        // Only get DIRECT children to avoid picking up nested values
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n.getParentNode() == parent) {
                return n.getTextContent() != null ? n.getTextContent().trim() : "";
            }
        }
        return list.item(0).getTextContent() != null
                ? list.item(0).getTextContent().trim() : "";
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRINT HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void printResult(BookingCodeResult result) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║          ✅  BOOKING CODE PARSED SUCCESSFULLY                    ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf ("║  Bookmaker  : %-50s ║%n", result.bookmaker());
        System.out.printf ("║  Code       : %-50s ║%n", result.bookingCode());
        System.out.printf ("║  Total Odds : %-50s ║%n", String.format("%.2f", result.totalOdds()));
        System.out.printf ("║  Games      : %-50s ║%n", result.totalSelections());
        System.out.printf ("║  Deadline   : %-50s ║%n", result.deadline());
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  GAMES IN THIS SLIP:                                             ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");

        for (int i = 0; i < result.selections().size(); i++) {
            SlipSelection s = result.selections().get(i);
            System.out.printf("║  #%d  %s  vs  %s%n",           i + 1, s.homeTeam(), s.awayTeam());
            System.out.printf("║       🏆 League    : %s (%s)%n", s.league(), s.country());
            System.out.printf("║       📊 Market    : %s%n",       s.market());
            System.out.printf("║       ✅ Pick      : %s%n",       s.outcome());
            System.out.printf("║       💰 Odds      : %.2f%n",     s.odds());
            System.out.printf("║       ⏰ Kickoff   : %s%n",       s.kickoffTime());
            System.out.printf("║       🔴 Status    : %s%s%n",     s.matchStatus(),
                    s.score().isEmpty() ? "" : " | Score: " + s.score());
            System.out.printf("║       🎫 Booking   : %s%s%n",    s.bookingStatus(),
                    s.isWinning() ? " ✅ WON" : "");
            if (i < result.selections().size() - 1)
                System.out.println("║       ─────────────────────────────────────────────────────── ║");
        }

        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();

        log.info("✅ Done — {} | Code: {} | Odds: {} | {} games",
                result.bookmaker(), result.bookingCode(),
                result.totalOdds(), result.totalSelections());
    }
}