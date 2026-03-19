package com.bettingPlatform.BettingWebsite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BookingCodeService
 *
 * ── Workflow ──────────────────────────────────────────────────────────────────
 *  1. Admin enters a booking code + bookmaker
 *  2. GET https://convertbetcodes.com/bet-viewer → extract CSRF token
 *  3. POST https://convertbetcodes.com/bet-viewer/retrieve_bet_code
 *       body: { csrf_new{ts}, code, origin_bookie }
 *  4. Response: { "view": "<html>...", "link": "..." }
 *  5. Parse HTML fragment → SlipSelection list
 *  6. Return BookingCodeResult
 *
 * ── Bookmaker codes for convertbetcodes ──────────────────────────────────────
 *  sportybet-gh  → sportybet:gh
 *  sportybet-ng  → sportybet:ng
 *  betway-gh     → betway:gh
 */
@Service
@Slf4j
public class BookingCodeService {

    private static final int TIMEOUT_MS = 20_000;

    private static final String CBC_VIEWER_URL = "https://convertbetcodes.com/bet-viewer";
    private static final String CBC_FETCH_URL  = "https://convertbetcodes.com/bet-viewer/retrieve_bet_code";

    private static final String USER_AGENT =
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

    /**
     * Main entry point — routes bookmaker key to convertbetcodes origin_bookie value.
     *
     * @param bookingCode  e.g. "8RF5L8"
     * @param bookmaker    "sportybet-gh" | "sportybet-ng" | "betway-gh"
     */
    public BookingCodeResult fetch(String bookingCode, String bookmaker) {
        String code         = bookingCode.trim().toUpperCase();
        String originBookie = resolveOriginBookie(bookmaker);
        String bookmakerName = resolveBookmakerName(bookmaker);

        log.info("📡 Fetching code={} bookmaker={} → originBookie={}", code, bookmaker, originBookie);
        return fetchViaConvertBetCodes(code, originBookie, bookmakerName);
    }

    // ── Bookmaker key → convertbetcodes origin_bookie value ───────────────────
    private String resolveOriginBookie(String bookmaker) {
        return switch (bookmaker.toLowerCase().trim()) {
            case "sportybet-gh", "sportybet_gh", "sportybet ghana"   -> "sportybet:gh";
            case "sportybet-ng", "sportybet_ng", "sportybet nigeria" -> "sportybet:ng";
            case "betway-gh",    "betway_gh",    "betway ghana"      -> "betway:gh";
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
    // STEP 1 + 2 + 3 — CSRF → POST → parse
    // ═══════════════════════════════════════════════════════════════════════════

    private BookingCodeResult fetchViaConvertBetCodes(
            String code, String originBookie, String bookmakerName) {
        try {

            // ── Step 1: GET /bet-viewer → extract CSRF token ───────────────────
            log.info("  ▶ Step 1: GET {} to fetch CSRF token", CBC_VIEWER_URL);

            Document viewerPage = Jsoup.connect(CBC_VIEWER_URL)
                    .userAgent(USER_AGENT)
                    .header("Accept",          "text/html,application/xhtml+xml,*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Ch-Ua",       "\"Chromium\";v=\"146\", \"Not-A.Brand\";v=\"24\", \"Google Chrome\";v=\"146\"")
                    .header("Sec-Ch-Ua-Mobile",   "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .timeout(TIMEOUT_MS)
                    .get();

            // Extract CSRF — input named csrf_new{timestamp}
            String csrfName  = null;
            String csrfValue = null;

            // Try via Jsoup selector first
            Elements csrfInputs = viewerPage.select("input[name^=csrf_]");
            if (!csrfInputs.isEmpty()) {
                Element el = csrfInputs.first();
                csrfName  = el.attr("name");
                csrfValue = el.attr("value");
                log.info("  ✅ CSRF via selector: {}={}", csrfName, csrfValue);
            }

            // Fallback: regex on raw HTML
            if (csrfName == null || csrfValue == null) {
                String html = viewerPage.html();
                Pattern p = Pattern.compile("name=[\"'](csrf_new\\d+)[\"']\\s+value=[\"']([a-f0-9]+)[\"']");
                Matcher m = p.matcher(html);
                if (m.find()) {
                    csrfName  = m.group(1);
                    csrfValue = m.group(2);
                    log.info("  ✅ CSRF via regex: {}={}", csrfName, csrfValue);
                }
            }

            if (csrfName == null || csrfValue == null) {
                // Proceed without CSRF — convertbetcodes may not require it for all requests
                log.warn("  ⚠ CSRF not found — proceeding without it");
                csrfName  = "csrf_new" + System.currentTimeMillis();
                csrfValue = "";
            }

            // ── Step 2: POST /bet-viewer/retrieve_bet_code ─────────────────────
            log.info("  ▶ Step 2: POST {} code={} bookie={}", CBC_FETCH_URL, code, originBookie);

            org.jsoup.Connection.Response response = Jsoup.connect(CBC_FETCH_URL)
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
                    .referrer(CBC_VIEWER_URL)
                    .data(csrfName,       csrfValue)
                    .data("code",         code)
                    .data("origin_bookie", originBookie)
                    .method(org.jsoup.Connection.Method.POST)
                    .ignoreContentType(true)
                    .timeout(TIMEOUT_MS)
                    .execute();

            String responseBody = response.body();
            log.info("  ✅ POST HTTP {} — {} chars", response.statusCode(), responseBody.length());

            printRaw("convertbetcodes", code, responseBody);

            // ── Step 3: Parse JSON → extract "view" HTML fragment ──────────────
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(responseBody);

            String viewHtml = json.path("view").asText("");
            String link     = json.path("link").asText("");

            if (viewHtml.isBlank()) {
                throw new RuntimeException(
                        "convertbetcodes returned empty view for code: " + code +
                                ". The code may be invalid or expired.");
            }

            log.info("  ✅ HTML fragment: {} chars | link: {}", viewHtml.length(), link);

            // ── Step 4: Parse HTML fragment ────────────────────────────────────
            return parseHtml(viewHtml, code, bookmakerName);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ convertbetcodes fetch failed for code {}: {}", code, e.getMessage());
            throw new RuntimeException(
                    "Failed to fetch code '" + code + "' from convertbetcodes: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HTML PARSER
    //
    // Actual HTML structure from convertbetcodes response (confirmed from network tab):
    //
    // Summary:
    //   <span>4events @55.87 odds</span>
    //
    // Each game — <li class="list-item">:
    //   <p class="tx-12 ... tx-color-03">1. Mexico Liga MX, Clausura</p>   ← league
    //   <p class="tx-medium ...">Club Necaxa - Club Tijuana</p>             ← home - away
    //   <p class="tx-12 ...">1X2 <b><span class="badg">Home</span></b></p> ← market + outcome
    //   <small class="badge bade-dark badge-pill">@2.20 odds</small>        ← odds
    //   <small class="badge tx-success bade-dark badge-pill">Mar 21, 02:00.utc</small> ← kickoff
    // ═══════════════════════════════════════════════════════════════════════════

    private BookingCodeResult parseHtml(String html, String bookingCode, String bookmakerName) {
        try {
            Document doc = Jsoup.parse(html);
            List<SlipSelection> selections = new ArrayList<>();
            double totalOdds = 0.0;

            // ── Extract total odds from summary ────────────────────────────────
            // e.g. "4events @55.87 odds"
            for (Element span : doc.select("span")) {
                String text = span.text();
                if (text.contains("@") && text.contains("odds")) {
                    try {
                        String oddsStr = text.replaceAll(".*@([\\d.]+)\\s*odds.*", "$1");
                        totalOdds = Double.parseDouble(oddsStr);
                        log.info("  📊 Total odds: {}", totalOdds);
                    } catch (Exception ignored) {}
                    break;
                }
            }

            // ── Parse each game ────────────────────────────────────────────────
            Elements gameItems = doc.select("li.list-item");
            log.info("  🔍 Found {} game items", gameItems.size());

            for (Element item : gameItems) {
                Elements paragraphs = item.select("p");

                String league  = "";
                String teams   = "";
                String market  = "";
                String outcome = "";

                for (Element p : paragraphs) {
                    String text = p.text().trim();
                    String cls  = p.className();

                    // League: "1. Mexico Liga MX, Clausura"
                    if (cls.contains("tx-color-03") && !cls.contains("tx-medium")) {
                        league = text.replaceAll("^\\d+\\.\\s*", "").trim();
                    }

                    // Teams: "Club Necaxa - Club Tijuana de Caliente"
                    if (cls.contains("tx-medium")) {
                        teams = text.trim();
                    }

                    // Market + outcome: "1X2 Home"
                    if (cls.contains("tx-12") && p.selectFirst("b") != null) {
                        Element bold = p.selectFirst("b");
                        outcome = bold != null ? bold.text().trim() : "";
                        market  = p.text().replace(outcome, "").trim();
                    }
                }

                // Odds: <small class="badge bade-dark ...">@2.20 odds</small>
                double odds = 0.0;
                for (Element small : item.select("small.bade-dark")) {
                    String t = small.text().trim();
                    if (t.startsWith("@")) {
                        try {
                            odds = Double.parseDouble(
                                    t.replace("@", "").replace("odds", "").trim());
                        } catch (Exception ignored) {}
                        break;
                    }
                }

                // Kickoff: <small class="badge tx-success ...">Mar 21, 02:00.utc</small>
                String kickoff = "";
                Elements kickoffEls = item.select("small.tx-success");
                if (!kickoffEls.isEmpty()) {
                    kickoff = kickoffEls.first().text().trim();
                }

                // Split "Home Team - Away Team"
                String homeTeam = "Unknown";
                String awayTeam = "Unknown";
                if (teams.contains(" - ")) {
                    String[] parts = teams.split(" - ", 2);
                    homeTeam = parts[0].trim();
                    awayTeam = parts[1].trim();
                } else if (!teams.isBlank()) {
                    homeTeam = teams;
                }

                // Skip empty rows
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

            // Recalculate total odds if not in summary
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
                    "Failed to parse convertbetcodes HTML for code: " + bookingCode +
                            " — " + e.getMessage(), e);
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
        log.debug("📦 Raw [{}/{}] — {} chars", label, code, raw != null ? raw.length() : 0);
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