package com.bettingPlatform.BettingWebsite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BookingCodeService
 *
 * ── Workflow ──────────────────────────────────────────────────────────────────
 *  1. Admin enters booking code + bookmaker
 *  2. GET https://convertbetcodes.com/bet-viewer
 *       → collect session COOKIES + extract CSRF token
 *  3. POST https://convertbetcodes.com/bet-viewer/retrieve_bet_code
 *       → send SAME cookies + csrf + code + origin_bookie
 *  4. Response: { "view": "<html>...", "link": "..." }
 *  5. Parse HTML fragment → SlipSelection list
 *
 * ── Why cookies matter ────────────────────────────────────────────────────────
 *  The CSRF token is tied to the PHP session (PHPSESSID cookie).
 *  Without sending the same cookies on the POST, the server rejects
 *  the CSRF as invalid and returns an HTML error page instead of JSON.
 *
 * ── origin_bookie values (must match their dropdown exactly) ─────────────────
 *  sportybet-gh  → "Sportybet -Ghana"
 *  sportybet-ng  → "Sportybet -Nigeria"
 *  betway-gh     → "Betway -Ghana"
 */
@Service
@Slf4j
public class BookingCodeService {

    private static final int    TIMEOUT_MS  = 20_000;
    private static final String VIEWER_URL  = "https://convertbetcodes.com/bet-viewer";
    private static final String FETCH_URL   = "https://convertbetcodes.com/bet-viewer/retrieve_bet_code";
    private static final String USER_AGENT  =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/146.0.0.0 Safari/537.36";

    // ═══════════════════════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════════════════════

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
            String rawJson
    ) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    public BookingCodeResult fetch(String bookingCode, String bookmaker) {
        String code          = bookingCode.trim().toUpperCase();
        String originBookie  = resolveOriginBookie(bookmaker);
        String bookmakerName = resolveBookmakerName(bookmaker);
        log.info("📡 fetch: code={} bookmaker={} → origin_bookie='{}'",
                code, bookmaker, originBookie);
        return fetchViaConvertBetCodes(code, originBookie, bookmakerName);
    }

    private String resolveOriginBookie(String bookmaker) {
        return switch (bookmaker.toLowerCase().trim()) {
            case "sportybet-gh", "sportybet_gh", "sportybet ghana"   -> "Sportybet -Ghana";
            case "sportybet-ng", "sportybet_ng", "sportybet nigeria" -> "Sportybet -Nigeria";
            case "betway-gh",    "betway_gh",    "betway ghana"      -> "Betway -Ghana";
            default -> throw new RuntimeException(
                    "Unsupported bookmaker: '" + bookmaker + "'. " +
                            "Accepted: sportybet-gh | sportybet-ng | betway-gh");
        };
    }

    private String resolveBookmakerName(String bookmaker) {
        return switch (bookmaker.toLowerCase().trim()) {
            case "sportybet-gh", "sportybet_gh", "sportybet ghana"   -> "Sportybet Ghana";
            case "sportybet-ng", "sportybet_ng", "sportybet nigeria" -> "Sportybet Nigeria";
            case "betway-gh",    "betway_gh",    "betway ghana"      -> "Betway Ghana";
            default -> bookmaker;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CORE FETCH
    // ═══════════════════════════════════════════════════════════════════════════

    private BookingCodeResult fetchViaConvertBetCodes(
            String code, String originBookie, String bookmakerName) {
        try {

            // ── Step 1: GET /bet-viewer ────────────────────────────────────────
            // Execute as Response so we can grab both cookies AND HTML
            log.info("  ▶ Step 1: GET {} (collecting cookies + CSRF)", VIEWER_URL);

            Connection.Response getResponse = Jsoup.connect(VIEWER_URL)
                    .userAgent(USER_AGENT)
                    .header("Accept",             "text/html,application/xhtml+xml,*/*")
                    .header("Accept-Language",    "en-US,en;q=0.9")
                    .header("Sec-Ch-Ua",          "\"Chromium\";v=\"146\", \"Not-A.Brand\";v=\"24\", \"Google Chrome\";v=\"146\"")
                    .header("Sec-Ch-Ua-Mobile",   "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .method(Connection.Method.GET)
                    .timeout(TIMEOUT_MS)
                    .execute();

            // ── Collect session cookies ────────────────────────────────────────
            Map<String, String> cookies = getResponse.cookies();
            log.info("  ✅ GET HTTP {} — cookies: {}", getResponse.statusCode(), cookies.keySet());

            // ── Extract CSRF token from HTML ───────────────────────────────────
            Document doc = getResponse.parse();

            String csrfName  = null;
            String csrfValue = null;

            // Try Jsoup selector first
            Elements csrfInputs = doc.select("input[name^=csrf_]");
            if (!csrfInputs.isEmpty()) {
                Element el = csrfInputs.first();
                csrfName  = el.attr("name");
                csrfValue = el.attr("value");
                log.info("  ✅ CSRF via selector: name={} value={}", csrfName, csrfValue);
            }

            // Regex fallback on raw HTML
            if (csrfName == null || csrfValue == null) {
                String html = doc.html();
                Pattern p = Pattern.compile(
                        "name=[\"'](csrf_new\\d+)[\"']\\s+value=[\"']([a-f0-9]+)[\"']");
                Matcher m = p.matcher(html);
                if (m.find()) {
                    csrfName  = m.group(1);
                    csrfValue = m.group(2);
                    log.info("  ✅ CSRF via regex: name={} value={}", csrfName, csrfValue);
                }
            }

            if (csrfName == null || csrfValue == null) {
                log.warn("  ⚠ CSRF token not found in page — proceeding without it");
                csrfName  = "csrf_new" + System.currentTimeMillis();
                csrfValue = "";
            }

            // ── Step 2: POST /bet-viewer/retrieve_bet_code ─────────────────────
            // CRITICAL: pass the same cookies collected from the GET
            log.info("  ▶ Step 2: POST {} | code={} origin_bookie='{}' | cookies={}",
                    FETCH_URL, code, originBookie, cookies.keySet());

            Connection.Response postResponse = Jsoup.connect(FETCH_URL)
                    .userAgent(USER_AGENT)
                    .header("Accept",             "*/*")
                    .header("Accept-Language",    "en-US,en;q=0.9")
                    .header("X-Requested-With",   "XMLHttpRequest")
                    .header("Sec-Ch-Ua",          "\"Chromium\";v=\"146\", \"Not-A.Brand\";v=\"24\", \"Google Chrome\";v=\"146\"")
                    .header("Sec-Ch-Ua-Mobile",   "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest",     "empty")
                    .header("Sec-Fetch-Mode",     "cors")
                    .header("Sec-Fetch-Site",     "same-origin")
                    .referrer(VIEWER_URL)
                    .cookies(cookies)                     // ← carry session cookies!
                    .data(csrfName,        csrfValue)     // ← CSRF tied to this session
                    .data("code",          code)
                    .data("origin_bookie", originBookie)
                    .method(Connection.Method.POST)
                    .ignoreContentType(true)
                    .timeout(TIMEOUT_MS)
                    .execute();

            String responseBody = postResponse.body();
            log.info("  ✅ POST HTTP {} — {} chars",
                    postResponse.statusCode(), responseBody.length());

            printRaw("convertbetcodes", code, responseBody);

            // ── Step 3: Parse JSON { "view": "<html>", "link": "..." } ─────────
            if (!responseBody.trim().startsWith("{")) {
                // Server returned HTML instead of JSON — log it for debugging
                log.error("  ❌ Response is not JSON. First 500 chars: {}",
                        responseBody.substring(0, Math.min(500, responseBody.length())));
                throw new RuntimeException(
                        "convertbetcodes returned an unexpected response (not JSON) for code: " + code +
                                ". The session or CSRF may have been rejected. " +
                                "First 200 chars: " + responseBody.substring(0, Math.min(200, responseBody.length())));
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(responseBody);

            String viewHtml = json.path("view").asText("");
            String link     = json.path("link").asText("");

            if (viewHtml.isBlank()) {
                log.error("  ❌ 'view' field is empty. Full JSON: {}", responseBody);
                throw new RuntimeException(
                        "convertbetcodes returned empty view for code: " + code +
                                ". Code may be invalid, expired, or the bookmaker value is wrong.");
            }

            log.info("  ✅ Got HTML fragment: {} chars | link: {}", viewHtml.length(), link);

            // ── Step 4: Parse HTML fragment ────────────────────────────────────
            return parseHtml(viewHtml, code, bookmakerName);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ fetch failed for code={}: {}", code, e.getMessage());
            throw new RuntimeException(
                    "Failed to fetch code '" + code + "' from convertbetcodes: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HTML PARSER
    //
    // Confirmed HTML structure from network tab:
    //
    // Summary:  <span>4events @55.87 odds</span>
    //
    // Per game — <li class="list-item">:
    //   <p class="tx-12 tx-color-03">1. Mexico Liga MX, Clausura</p>    ← league
    //   <p class="tx-medium">Club Necaxa - Club Tijuana</p>              ← teams
    //   <p class="tx-12">1X2 <b><span class="badg">Home</span></b></p>  ← market + outcome
    //   <small class="badge bade-dark badge-pill">@2.20 odds</small>     ← odds
    //   <small class="badge tx-success bade-dark">Mar 21, 02:00.utc</small> ← kickoff
    // ═══════════════════════════════════════════════════════════════════════════

    private BookingCodeResult parseHtml(String html, String bookingCode, String bookmakerName) {
        try {
            Document doc = Jsoup.parse(html);
            List<SlipSelection> selections = new ArrayList<>();
            double totalOdds = 0.0;

            // ── Total odds from summary span ───────────────────────────────────
            for (Element span : doc.select("span")) {
                String text = span.text();
                if (text.contains("@") && text.contains("odds")) {
                    try {
                        totalOdds = Double.parseDouble(
                                text.replaceAll(".*@([\\d.]+)\\s*odds.*", "$1"));
                        log.info("  📊 Total odds: {}", totalOdds);
                    } catch (Exception ignored) {}
                    break;
                }
            }

            // ── Parse each game ────────────────────────────────────────────────
            Elements gameItems = doc.select("li.list-item");
            log.info("  🔍 Found {} game items", gameItems.size());

            for (Element item : gameItems) {
                String league  = "";
                String teams   = "";
                String market  = "";
                String outcome = "";

                for (Element p : item.select("p")) {
                    String text = p.text().trim();
                    String cls  = p.className();

                    // League — "1. Mexico Liga MX, Clausura"
                    if (cls.contains("tx-color-03") && !cls.contains("tx-medium")) {
                        league = text.replaceAll("^\\d+\\.\\s*", "").trim();
                    }
                    // Teams — "Club Necaxa - Club Tijuana"
                    if (cls.contains("tx-medium")) {
                        teams = text.trim();
                    }
                    // Market + outcome — "1X2 Home"
                    if (cls.contains("tx-12") && p.selectFirst("b") != null) {
                        Element bold = p.selectFirst("b");
                        outcome = bold != null ? bold.text().trim() : "";
                        market  = p.text().replace(outcome, "").trim();
                    }
                }

                // Odds — @2.20 odds
                double odds = 0.0;
                for (Element small : item.select("small.bade-dark")) {
                    String t = small.text().trim();
                    if (t.startsWith("@")) {
                        try { odds = Double.parseDouble(
                                t.replace("@","").replace("odds","").trim()); }
                        catch (Exception ignored) {}
                        break;
                    }
                }

                // Kickoff
                String kickoff = "";
                Elements kickoffEls = item.select("small.tx-success");
                if (!kickoffEls.isEmpty()) kickoff = kickoffEls.first().text().trim();

                // Split teams
                String homeTeam = "Unknown", awayTeam = "Unknown";
                if (teams.contains(" - ")) {
                    String[] parts = teams.split(" - ", 2);
                    homeTeam = parts[0].trim();
                    awayTeam = parts[1].trim();
                } else if (!teams.isBlank()) {
                    homeTeam = teams;
                }

                if (homeTeam.equals("Unknown") && league.isBlank()) continue;

                SlipSelection sel = new SlipSelection(
                        homeTeam,
                        awayTeam,
                        market.isBlank()  ? "1X2"     : market,
                        outcome.isBlank() ? "Unknown" : outcome,
                        odds,
                        kickoff.isBlank() ? "N/A"     : kickoff,
                        league.isBlank()  ? "Unknown" : league
                );
                selections.add(sel);

                log.info("  🎯 [{}] {} vs {} | {} → {} @ {} | ⏰ {}",
                        sel.league(), sel.homeTeam(), sel.awayTeam(),
                        sel.market(), sel.outcome(), sel.odds(), sel.kickoffTime());
            }

            if (selections.isEmpty()) {
                throw new RuntimeException(
                        "No games found for code: " + bookingCode +
                                ". The code may be expired or invalid.");
            }

            if (totalOdds == 0.0) {
                totalOdds = selections.stream()
                        .mapToDouble(SlipSelection::odds)
                        .reduce(1.0, (a, b) -> a * b);
                totalOdds = Math.round(totalOdds * 100.0) / 100.0;
            }

            BookingCodeResult result = new BookingCodeResult(
                    bookmakerName, bookingCode, totalOdds,
                    selections.size(), selections,
                    "[via convertbetcodes.com]");

            printResult(result);
            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ HTML parse error: {}", e.getMessage());
            throw new RuntimeException(
                    "Failed to parse convertbetcodes HTML: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRINT HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void printRaw(String label, String code, String raw) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.printf ("📦 RAW — %s | Code: %s%n", label, code);
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println(raw);
        System.out.println("═══════════════════════════════════════════════════════════════");
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
            System.out.printf("║  #%d  %s  vs  %s%n",        i + 1, s.homeTeam(), s.awayTeam());
            System.out.printf("║       🏆 League  : %s%n",   s.league());
            System.out.printf("║       📊 Market  : %s%n",   s.market());
            System.out.printf("║       ✅ Pick    : %s%n",   s.outcome());
            System.out.printf("║       💰 Odds    : %.2f%n", s.odds());
            System.out.printf("║       ⏰ Kickoff : %s%n",   s.kickoffTime());
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