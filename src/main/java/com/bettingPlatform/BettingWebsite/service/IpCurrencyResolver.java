package com.bettingPlatform.BettingWebsite.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

/**
 * Resolves a user's preferred Paystack payment currency from their IP address.
 *
 * Uses ip-api.com (free tier — no API key, 45 req/min).
 * Country → Paystack currency mapping:
 *   Ghana   (GH) → GHS
 *   Nigeria (NG) → NGN
 *   All others   → USD  (Paystack universal fallback)
 *
 * Supports an optional user override so the user can still manually switch
 * currency on the frontend (e.g. a Ghanaian user wanting to pay in USD).
 */
@Component
@Slf4j
public class IpCurrencyResolver {

    private final RestTemplate restTemplate;

    // ip-api.com free JSON endpoint — returns countryCode in response
    private static final String IP_API_URL =
            "http://ip-api.com/json/%s?fields=status,countryCode";

    // The three currencies this platform accepts via Paystack
    public static final Set<String> PAYSTACK_CURRENCIES = Set.of("GHS", "NGN", "USD");

    // Country ISO-2 code → Paystack-supported currency
    private static final Map<String, String> COUNTRY_CURRENCY_MAP = Map.ofEntries(
            Map.entry("GH", "GHS"),   // Ghana
            Map.entry("NG", "NGN"),   // Nigeria
            Map.entry("US", "USD"),   // United States
            Map.entry("GB", "USD"),   // UK        — Paystack GBP not yet supported
            Map.entry("CA", "USD"),   // Canada
            Map.entry("AU", "USD"),   // Australia
            Map.entry("KE", "USD"),   // Kenya     — Paystack KES not widely supported
            Map.entry("ZA", "USD"),   // South Africa
            Map.entry("TZ", "USD"),   // Tanzania
            Map.entry("UG", "USD"),   // Uganda
            Map.entry("SN", "USD"),   // Senegal
            Map.entry("CI", "USD"),   // Côte d'Ivoire
            Map.entry("CM", "USD")    // Cameroon
    );

    public IpCurrencyResolver(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ─────────────────────────────────────────────────────────────
    // Primary: resolve currency from IP only
    // ─────────────────────────────────────────────────────────────

    /**
     * Detects the best Paystack-supported currency for a given IP address.
     * Falls back to USD if the IP is private, unknown, or the API fails.
     *
     * @param ipAddress  raw client IP (may be null / blank / private)
     * @return           "GHS", "NGN", or "USD"
     */
    @SuppressWarnings("unchecked")
    public String resolveCurrency(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || isPrivateIp(ipAddress)) {
            log.debug("🌍 Private/missing IP ({}) — defaulting currency to GHS (dev mode)", ipAddress);
            return "GHS";
        }

        try {
            String url = String.format(IP_API_URL, ipAddress.trim());
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null || !"success".equals(body.get("status"))) {
                log.warn("⚠️ ip-api.com returned non-success for IP {} — defaulting to USD", ipAddress);
                return "USD";
            }

            String countryCode = (String) body.get("countryCode"); // e.g. "GH", "NG"
            String resolved    = COUNTRY_CURRENCY_MAP.getOrDefault(countryCode, "USD");

            log.info("🌍 IP {} → country {} → currency {}", ipAddress, countryCode, resolved);
            return resolved;

        } catch (Exception e) {
            log.warn("⚠️ IP lookup failed for {}: {} — defaulting to USD", ipAddress, e.getMessage());
            return "USD";
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Secondary: resolve with optional user manual override
    // ─────────────────────────────────────────────────────────────

    /**
     * Resolves currency from IP but lets the user override it manually.
     * The override must be a valid Paystack-supported currency; otherwise it is ignored.
     *
     * @param ipAddress    raw client IP
     * @param userOverride currency code from the user's frontend selection (nullable)
     * @return             "GHS", "NGN", or "USD"
     */
    public String resolveCurrencyWithOverride(String ipAddress, String userOverride) {
        if (userOverride != null
                && !userOverride.isBlank()
                && PAYSTACK_CURRENCIES.contains(userOverride.toUpperCase())) {
            log.info("💱 User manually selected currency: {}", userOverride.toUpperCase());
            return userOverride.toUpperCase();
        }
        return resolveCurrency(ipAddress);
    }

    // ─────────────────────────────────────────────────────────────
    // Helper: extract real IP behind proxy / load balancer
    // ─────────────────────────────────────────────────────────────

    /**
     * Extracts the real client IP from the request, accounting for
     * reverse proxies that set X-Forwarded-For.
     *
     * Usage in controller:
     *   String ip = ipCurrencyResolver.extractIp(request);
     *
     * @param request  HttpServletRequest from the controller
     * @return         best-guess real IP
     */
    public String extractIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can be a comma-separated list; first entry is the real client
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns true if the IP is a loopback or private-range address
     * (i.e. running locally in dev — we can't look these up).
     */
    private boolean isPrivateIp(String ip) {
        return ip.startsWith("127.")
                || ip.startsWith("192.168.")
                || ip.startsWith("10.")
                || ip.startsWith("172.16.")
                || ip.startsWith("172.17.")
                || ip.startsWith("172.18.")
                || ip.startsWith("172.19.")
                || ip.startsWith("172.2")
                || ip.startsWith("172.30.")
                || ip.startsWith("172.31.")
                || ip.equals("0:0:0:0:0:0:0:1")
                || ip.equals("::1");
    }
}