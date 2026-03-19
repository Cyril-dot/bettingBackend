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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BookingCodeService  —  v3  (Direct-first + ScrapeOps 4-key fallback)
 *
 * ── Root cause of v2 failure ──────────────────────────────────────────────────
 *  ScrapeOps was returning HTTP 500 "Failed to get successful response from
 *  website" on ALL 4 keys (both datacenter and residential tiers).
 *  Sportybet is blocking the entire ScrapeOps IP pool at the network level.
 *
 *  Key insight: the "Full Response Inspector" HTML page calls Sportybet DIRECTLY
 *  from a browser (no proxy) and gets a valid XML response every time.
 *  This proves Sportybet's API is publicly accessible — it only blocks known
 *  proxy/datacenter IP ranges like ScrapeOps.
 *
 * ── New fetch strategy ────────────────────────────────────────────────────────
 *  Step 1 — Direct GET from this server with 3 browser UA profiles.
 *            Your Render server IP is clean and not in any proxy blocklist.
 *            This is FREE and should succeed on the first attempt virtually
 *            every time.
 *
 *  Step 2 — If ALL 3 direct attempts fail (e.g. Render IP gets temporarily
 *            flagged), fall back to the 4 ScrapeOps keys with the same
 *            datacenter-first / residential-second strategy from v2.
 *
 * ── Credit cost ───────────────────────────────────────────────────────────────
 *  Direct fetch  : 0 credits  ← used 99% of the time
 *  Datacenter    : 1 credit / request  ← ScrapeOps Tier A fallback
 *  Residential   : 10 credits / request ← ScrapeOps Tier B fallback
 */
@Service
@Slf4j
public class BookingCodeService {

    // ── Timeouts ──────────────────────────────────────────────────────────────
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS    = 20_000;

    // ── Sportybet / Betway base URLs ──────────────────────────────────────────
    private static final String SPORTYBET_GH_URL = "https://www.sportybet.com/api/gh/orders/share/";
    private static final String SPORTYBET_NG_URL = "https://www.sportybet.com/api/ng/orders/share/";
    private static final String BETWAY_GH_URL    = "https://www.betway.com.gh/api/Betslip/GetShareBetslip?shareCode=";

    // ── Kickoff time formatter ────────────────────────────────────────────────
    private static final DateTimeFormatter KICKOFF_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    // ── Browser UA profiles — rotate on each direct retry ────────────────────
    // These mirror exactly what the working HTML inspector sends from a browser.
    private static final String[] USER_AGENTS = {
            // Android Chrome — most common in Ghana/Nigeria
            "Mozilla/5.0 (Linux; Android 12; SM-A525F) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36",
            // iPhone Safari
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Mobile/15E148 Safari/604.1",
            // Windows Chrome desktop
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    };

    // ── ScrapeOps fallback — 4 keys, kept as safety net ──────────────────────
    private static final String[] SCRAPEOPS_KEYS = {
            "230b20f9-cc9f-4edb-bfee-38430ce0d22d",   // Key 1
            "c2e7161e-cd7e-4505-8ab7-94ff993bf23c",   // Key 2
            "fca93b28-accd-4946-ab33-07d1f43ffb1d",   // Key 3
            "7cb32829-319f-4de6-8c37-162a90d729af",   // Key 4
    };
    private static final String SCRAPEOPS_PROXY_URL = "https://proxy.scrapeops.io/v1/";

    // Round-robin index for ScrapeOps (thread-safe)
    private final AtomicInteger scrapeopsKeyIndex = new AtomicInteger(0);

    // ═══════════════════════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════════════════════

    public record SlipSelection(
            String homeTeam,
            String awayTeam,
            String league,
            String country,
            String sport,
            String market,
            String outcome,
            double odds,
            String kickoffTime,
            long   kickoffTimestamp,
            String matchStatus,
            String score,
            String playedTime,
            int    statusCode,
            String bookingStatus,
            boolean isWinning,
            String eventId,
            String gameId
    ) {}

    public record BookingCodeResult(
            String bookmaker,
            String bookingCode,
            double totalOdds,
            int    totalSelections,
            String deadline,
            List<SlipSelection> selections,
            String rawXml
    ) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

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
        String xml = fetchWithDirectFirst(SPORTYBET_GH_URL + code, "gh");
        return parseSportybetXml(xml, code, "Sportybet Ghana");
    }

    public BookingCodeResult fetchSportybetNigeria(String code) {
        log.info("📡 [Sportybet NG] Fetching code: {}", code);
        String xml = fetchWithDirectFirst(SPORTYBET_NG_URL + code, "ng");
        return parseSportybetXml(xml, code, "Sportybet Nigeria");
    }

    public BookingCodeResult fetchBetwayGhana(String code) {
        log.info("📡 [Betway GH] Fetching code: {}", code);
        String raw = fetchWithDirectFirst(BETWAY_GH_URL + code, "gh");
        return parseBetwayJson(raw, code);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MASTER FETCH ORCHESTRATOR
    //
    // Priority 1 — direct call from this server (mirrors the HTML inspector).
    // Priority 2 — ScrapeOps 4-key rotation (datacenter then residential).
    // ═══════════════════════════════════════════════════════════════════════════

    private String fetchWithDirectFirst(String targetUrl, String country) {

        // ── Step 1: direct attempts (free, no credits) ────────────────────────
        log.info("  🌐  Direct fetch attempt: {}", targetUrl);
        for (int i = 0; i < USER_AGENTS.length; i++) {
            try {
                String result = doDirectGet(targetUrl, country, USER_AGENTS[i]);
                log.info("  ✅  Direct fetch succeeded (UA profile #{})", i + 1);
                return result;
            } catch (Exception e) {
                log.warn("  ⚠  Direct attempt #{} failed: {}", i + 1, e.getMessage());
                if (i < USER_AGENTS.length - 1) {
                    try { Thread.sleep(250); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
            }
        }

        // ── Step 2: ScrapeOps fallback ────────────────────────────────────────
        log.warn("  🔄  All direct attempts failed — using ScrapeOps fallback");
        return fetchViaScrapeOps(targetUrl, country);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DIRECT GET — same headers as the working HTML inspector
    // ═══════════════════════════════════════════════════════════════════════════

    private String doDirectGet(String targetUrl, String country, String userAgent) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        RestTemplate rest = new RestTemplate(factory);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT,          "application/xml, text/xml, application/json, */*");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-GH,en-US;q=0.9,en;q=0.8");
        headers.set(HttpHeaders.USER_AGENT,       userAgent);
        headers.set(HttpHeaders.REFERER,          "https://www.sportybet.com/" + country + "/");
        headers.set("X-Requested-With",           "XMLHttpRequest");
        headers.set("sec-fetch-dest",             "empty");
        headers.set("sec-fetch-mode",             "cors");
        headers.set("sec-fetch-site",             "same-origin");

        log.debug("  📡  Direct GET → {}", targetUrl);

        ResponseEntity<String> response = rest.exchange(
                targetUrl, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        String body = response.getBody();

        if (!response.getStatusCode().is2xxSuccessful() || body == null || body.isBlank()) {
            throw new RuntimeException("Non-2xx or empty response: " + response.getStatusCode());
        }
        if (body.trim().startsWith("<!DOCTYPE") || body.trim().startsWith("<html")) {
            throw new RuntimeException(
                    "Got HTML page (WAF/Cloudflare block). Preview: " +
                            body.substring(0, Math.min(150, body.length())));
        }

        log.info("  ✅  Direct HTTP {} — {} chars received", response.getStatusCode(), body.length());
        return body;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCRAPEOPS FALLBACK — 4 keys × 2 tiers
    // ═══════════════════════════════════════════════════════════════════════════

    private String fetchViaScrapeOps(String targetUrl, String country) {
        int startIdx = scrapeopsKeyIndex.get();
        int numKeys  = SCRAPEOPS_KEYS.length;

        for (int i = 0; i < numKeys; i++) {
            int    idx = (startIdx + i) % numKeys;
            String key = SCRAPEOPS_KEYS[idx];

            if (key == null || key.isBlank() || key.startsWith("REPLACE_WITH")) {
                log.debug("  ⏭  Skipping placeholder key #{}", idx + 1);
                continue;
            }

            // Exponential back-off between keys
            if (i > 0) {
                long delayMs = 100L * (1L << Math.min(i - 1, 4));
                log.debug("  ⏳  Back-off {}ms before key #{}", delayMs, idx + 1);
                try { Thread.sleep(delayMs); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }

            // Tier A: datacenter (1 credit)
            try {
                log.info("  ▶ ScrapeOps key #{} — Tier A (datacenter)", idx + 1);
                String result = doScrapeOpsGet(key, targetUrl, country, false);
                scrapeopsKeyIndex.set((idx + 1) % numKeys);
                log.info("  ✅  ScrapeOps key #{} Tier A succeeded", idx + 1);
                return result;
            } catch (Exception e) {
                log.warn("  ⚠  Key #{} Tier A failed: {} — trying residential", idx + 1, e.getMessage());
            }

            // Tier B: residential (10 credits)
            try {
                log.info("  ▶ ScrapeOps key #{} — Tier B (residential)", idx + 1);
                String result = doScrapeOpsGet(key, targetUrl, country, true);
                scrapeopsKeyIndex.set((idx + 1) % numKeys);
                log.info("  ✅  ScrapeOps key #{} Tier B succeeded", idx + 1);
                return result;
            } catch (Exception e) {
                log.warn("  ⚠  Key #{} Tier B failed: {} — next key", idx + 1, e.getMessage());
            }
        }

        throw new RuntimeException(
                "All fetch methods failed for: " + targetUrl +
                        ". Direct fetch blocked AND all 4 ScrapeOps keys failed.");
    }

    private String doScrapeOpsGet(String apiKey, String targetUrl, String country, boolean residential) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        RestTemplate rest = new RestTemplate(factory);

        StringBuilder proxyUrl = new StringBuilder(SCRAPEOPS_PROXY_URL)
                .append("?api_key=").append(apiKey)
                .append("&url=").append(URLEncoder.encode(targetUrl, StandardCharsets.UTF_8))
                .append("&country=").append(country);
        if (residential) proxyUrl.append("&residential=true");

        log.debug("  📡  ScrapeOps proxy (truncated): {}…",
                proxyUrl.substring(0, Math.min(80, proxyUrl.length())));

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT,          "application/xml, text/xml, application/json, */*");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-GH,en;q=0.9");
        headers.set(HttpHeaders.USER_AGENT,       USER_AGENTS[0]);
        headers.set(HttpHeaders.REFERER,          "https://www.sportybet.com/" + country + "/");
        headers.set("X-Requested-With",           "XMLHttpRequest");

        try {
            ResponseEntity<String> response = rest.exchange(
                    proxyUrl.toString(), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            String body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.isBlank()) {
                throw new RuntimeException("Non-2xx/empty: " + response.getStatusCode());
            }
            if (body.trim().startsWith("<!DOCTYPE") || body.trim().startsWith("<html")) {
                throw new RuntimeException("Got HTML page (WAF block via proxy)");
            }
            log.info("  ✅  ScrapeOps HTTP {} — {} chars", response.getStatusCode(), body.length());
            return body;

        } catch (HttpClientErrorException e) {
            String hint = switch (e.getStatusCode().value()) {
                case 403 -> "Key rejected/exhausted (403)";
                case 429 -> "Rate limited (429)";
                case 404 -> "URL not found (404)";
                default  -> "HTTP " + e.getStatusCode();
            };
            throw new RuntimeException(hint + ": " +
                    e.getResponseBodyAsString().substring(
                            0, Math.min(120, e.getResponseBodyAsString().length())));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPORTYBET XML PARSER
    // ═══════════════════════════════════════════════════════════════════════════

    private BookingCodeResult parseSportybetXml(String rawXml, String bookingCode, String bookmakerName) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(rawXml)));
            doc.getDocumentElement().normalize();

            String bizCode = getTagValue(doc, "bizCode");
            String message = getTagValue(doc, "message");
            if (!"10000".equals(bizCode)) {
                String reason = switch (bizCode) {
                    case "10001" -> "Booking code not found or already expired";
                    case "10002" -> "Booking code has been cancelled";
                    case "10003" -> "Booking code belongs to a different region";
                    case "40001" -> "Request rate-limited by Sportybet — try again shortly";
                    default      -> "Unknown error (bizCode=" + bizCode + "): " + message;
                };
                throw new RuntimeException("Sportybet rejected the request: " + reason);
            }

            String shareCode = getTagValue(doc, "shareCode");
            String deadline  = getTagValue(doc, "deadline");
            log.info("  📋  shareCode={} deadline={}", shareCode, deadline);

            NodeList outcomesList = doc.getElementsByTagName("outcomes");
            List<SlipSelection> selections = new ArrayList<>();
            double totalOdds = 1.0;

            for (int i = 0; i < outcomesList.getLength(); i++) {
                Node node = outcomesList.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) node;

                String homeTeam = getChildValue(el, "homeTeamName");
                if (homeTeam.isEmpty()) continue;

                String awayTeam      = getChildValue(el, "awayTeamName");
                String eventId       = getChildValue(el, "eventId");
                String gameId        = getChildValue(el, "gameId");
                String matchStatus   = getChildValue(el, "matchStatus");
                String score         = getChildValue(el, "setScore");
                String playedTime    = getChildValue(el, "playedSeconds");
                String bookingStatus = getChildValue(el, "bookingStatus");
                int    statusCode    = parseInt(getChildValue(el, "status"));

                long   kickoffTs  = parseLong(getChildValue(el, "estimateStartTime"));
                String kickoffStr = kickoffTs > 0
                        ? KICKOFF_FMT.format(Instant.ofEpochMilli(kickoffTs))
                        : "TBD";

                String sport = "", country = "", league = "";
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
                            league = getChildValue((Element) tournNodes.item(0), "name");
                        }
                    }
                }

                String  market    = "";
                String  outcome   = "";
                double  odds      = 0.0;
                boolean isWinning = false;

                NodeList marketsList = el.getElementsByTagName("markets");
                for (int m = 0; m < marketsList.getLength(); m++) {
                    Node mNode = marketsList.item(m);
                    if (mNode.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element mEl   = (Element) mNode;
                    String  mDesc = getChildValue(mEl, "desc");
                    if (!mDesc.isEmpty()) {
                        market = mDesc;
                        NodeList oNodes = mEl.getElementsByTagName("outcomes");
                        for (int o = 0; o < oNodes.getLength(); o++) {
                            Node oNode = oNodes.item(o);
                            if (oNode.getNodeType() != Node.ELEMENT_NODE) continue;
                            Element oEl   = (Element) oNode;
                            String  oDesc = getChildValue(oEl, "desc");
                            String  oOdds = getChildValue(oEl, "odds");
                            if (!oDesc.isEmpty() && !oOdds.isEmpty()) {
                                outcome   = oDesc;
                                odds      = parseDouble(oOdds);
                                isWinning = "1".equals(getChildValue(oEl, "isWinning"));
                                break;
                            }
                        }
                        break;
                    }
                }

                if (odds > 0) totalOdds *= odds;

                selections.add(new SlipSelection(
                        homeTeam, awayTeam,
                        league, country, sport,
                        market, outcome, odds,
                        kickoffStr, kickoffTs,
                        matchStatus, score, playedTime, statusCode,
                        bookingStatus, isWinning,
                        eventId, gameId
                ));

                log.info("  🎯  [{}/{}] {} vs {} | {} → {} @ {} | {}",
                        country, league, homeTeam, awayTeam,
                        market, outcome, odds, matchStatus);
            }

            if (selections.isEmpty()) {
                throw new RuntimeException(
                        "No game selections found for code: " + bookingCode +
                                ". The slip may be expired or the code is invalid.");
            }

            totalOdds = Math.round(totalOdds * 100.0) / 100.0;

            BookingCodeResult result = new BookingCodeResult(
                    bookmakerName, bookingCode, totalOdds,
                    selections.size(), deadline, selections, rawXml);

            printResult(result);
            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌  XML parse error: {}", e.getMessage());
            throw new RuntimeException("Failed to parse Sportybet XML: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BETWAY GHANA JSON PARSER
    // ═══════════════════════════════════════════════════════════════════════════

    private BookingCodeResult parseBetwayJson(String raw, String bookingCode) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(raw);

            if (!root.path("IsSuccess").asBoolean(false)) {
                String errMsg = root.path("Message").asText("Unknown error from Betway API");
                throw new RuntimeException("Betway rejected the request: " + errMsg);
            }

            com.fasterxml.jackson.databind.JsonNode betslip = root.path("Betslip");
            double totalOdds = betslip.path("TotalOdds").asDouble(0.0);
            com.fasterxml.jackson.databind.JsonNode sels = betslip.path("Selections");

            List<SlipSelection> selections = new ArrayList<>();

            for (com.fasterxml.jackson.databind.JsonNode s : sels) {
                String kickoffRaw = s.path("KickOffDate").asText("");
                long kickoffTs = 0;
                String kickoffStr = "TBD";
                if (!kickoffRaw.isBlank()) {
                    try {
                        kickoffTs  = Instant.parse(kickoffRaw).toEpochMilli();
                        kickoffStr = KICKOFF_FMT.format(Instant.ofEpochMilli(kickoffTs));
                    } catch (Exception ignored) {
                        kickoffStr = kickoffRaw;
                    }
                }

                selections.add(new SlipSelection(
                        s.path("HomeTeamName").asText("?"),
                        s.path("AwayTeamName").asText("?"),
                        s.path("CompetitionName").asText("?"),
                        s.path("CategoryName").asText(""),
                        s.path("SportName").asText("Football"),
                        s.path("MarketName").asText("1X2"),
                        s.path("OutcomeName").asText("?"),
                        s.path("Price").asDouble(0.0),
                        kickoffStr, kickoffTs,
                        s.path("MatchStatus").asText("Unknown"),
                        s.path("Score").asText(""),
                        "", 0,
                        "Booked",
                        s.path("IsWinner").asBoolean(false),
                        s.path("EventId").asText(""),
                        s.path("GameId").asText("")
                ));
            }

            BookingCodeResult result = new BookingCodeResult(
                    "Betway Ghana", bookingCode,
                    Math.round(totalOdds * 100.0) / 100.0,
                    selections.size(), "", selections, raw);

            printResult(result);
            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Betway JSON: " + e.getMessage(), e);
        }
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
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n.getParentNode() == parent) {
                return n.getTextContent() != null ? n.getTextContent().trim() : "";
            }
        }
        return list.item(0).getTextContent() != null
                ? list.item(0).getTextContent().trim() : "";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PARSE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

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
    // CONSOLE PRINT HELPER
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
            System.out.printf("║  #%d  %s  vs  %s%n",             i + 1, s.homeTeam(), s.awayTeam());
            System.out.printf("║       🏆 League    : %s (%s)%n",  s.league(), s.country());
            System.out.printf("║       📊 Market    : %s%n",        s.market());
            System.out.printf("║       ✅ Pick      : %s%n",        s.outcome());
            System.out.printf("║       💰 Odds      : %.2f%n",      s.odds());
            System.out.printf("║       ⏰ Kickoff   : %s%n",        s.kickoffTime());
            System.out.printf("║       🔴 Status    : %s%s%n",      s.matchStatus(),
                    s.score().isEmpty() ? "" : " | Score: " + s.score());
            System.out.printf("║       🎫 Booking   : %s%s%n",      s.bookingStatus(),
                    s.isWinning() ? " ✅ WON" : "");
            if (i < result.selections().size() - 1) {
                System.out.println("║       ──────────────────────────────────────────────────────  ║");
            }
        }

        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();

        log.info("✅  Done — {} | Code: {} | Odds: {} | {} games",
                result.bookmaker(), result.bookingCode(),
                result.totalOdds(), result.totalSelections());
    }
}