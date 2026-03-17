package com.bettingPlatform.BettingWebsite.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches live exchange rates from open.er-api.com (free tier — no API key).
 *
 * The admin can set the VIP price in ANY supported currency (USD, GHS, EUR, etc.).
 * This service converts that base price to whatever the user's local
 * Paystack currency is at the moment of payment.
 *
 * Cross-conversion logic (handles any base → any target):
 *   Step 1: baseCurrency  → USD  (divide by USD→base rate)
 *   Step 2: USD           → targetCurrency (multiply by USD→target rate)
 *
 * Example — admin set 50 GHS, user pays in NGN:
 *   50 GHS ÷ 15.5 = 3.226 USD  →  3.226 × 1600 = 5,161 NGN
 *
 * Rates are cached for 1 hour so we don't hammer the free API.
 * A hardcoded fallback map is used if the live API is unreachable.
 */
@Component
@Slf4j
public class CurrencyConverter {

    private final RestTemplate restTemplate;

    // Currencies the admin is allowed to set the base price in
    public static final Set<String> SUPPORTED_BASE_CURRENCIES =
            Set.of("USD", "GHS", "NGN", "EUR", "GBP");

    // open.er-api.com — free, no key, USD-based rates
    private static final String RATE_URL = "https://open.er-api.com/v6/latest/USD";

    // Cache TTL — 1 hour
    private static final long CACHE_TTL_MS = 60 * 60 * 1000L;

    // Thread-safe in-memory cache
    private final AtomicReference<Map<String, Double>> cachedRates = new AtomicReference<>(null);
    private final AtomicLong cacheTimestamp = new AtomicLong(0);

    // Fallback rates (1 USD = X) — used only when live API is down
    private static final Map<String, Double> FALLBACK_RATES = Map.of(
            "USD", 1.0,
            "GHS", 15.5,
            "NGN", 1600.0,
            "KES", 130.0,
            "ZAR", 18.5,
            "GBP", 0.79,
            "EUR", 0.92
    );

    public CurrencyConverter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ─────────────────────────────────────────────────────────────
    // Primary conversion method
    // ─────────────────────────────────────────────────────────────

    /**
     * Converts an amount from the admin's chosen base currency to the
     * user's Paystack payment currency using live exchange rates.
     *
     * @param amount         price the admin configured  (e.g. 10.00)
     * @param baseCurrency   admin's currency            (e.g. "USD")
     * @param targetCurrency user's Paystack currency    (e.g. "NGN")
     * @return               converted amount rounded to 2 decimal places
     */
    public double convert(double amount, String baseCurrency, String targetCurrency) {
        String base   = baseCurrency.toUpperCase();
        String target = targetCurrency.toUpperCase();

        // No conversion needed
        if (base.equals(target)) return round(amount);

        Map<String, Double> rates = fetchRates();  // USD-based rates

        // Get USD→base and USD→target rates
        double usdToBase   = rates.getOrDefault(base,   FALLBACK_RATES.getOrDefault(base,   1.0));
        double usdToTarget = rates.getOrDefault(target, FALLBACK_RATES.getOrDefault(target, 1.0));

        // Cross-convert:  amount (base) → USD → target
        double inUsd      = amount / usdToBase;
        double converted  = round(inUsd * usdToTarget);

        log.debug("💱 {} {} → {:.4f} USD → {} {}  (rates: 1 USD = {} {}, 1 USD = {} {})",
                amount, base, inUsd, converted, target,
                usdToBase, base, usdToTarget, target);

        return converted;
    }

    // ─────────────────────────────────────────────────────────────
    // Convenience: get the live rate for display purposes
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns: how many units of targetCurrency equal 1 unit of baseCurrency.
     * Used for metadata logging and frontend display ("1 USD = 15.5 GHS").
     */
    public double getRate(String baseCurrency, String targetCurrency) {
        String base   = baseCurrency.toUpperCase();
        String target = targetCurrency.toUpperCase();
        if (base.equals(target)) return 1.0;

        Map<String, Double> rates = fetchRates();
        double usdToBase   = rates.getOrDefault(base,   FALLBACK_RATES.getOrDefault(base,   1.0));
        double usdToTarget = rates.getOrDefault(target, FALLBACK_RATES.getOrDefault(target, 1.0));
        return round(usdToTarget / usdToBase);
    }

    // ─────────────────────────────────────────────────────────────
    // Live rate fetch with 1-hour cache + fallback
    // ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Double> fetchRates() {
        long now    = System.currentTimeMillis();
        Map<String, Double> cached = cachedRates.get();

        // Return cache if still fresh
        if (cached != null && (now - cacheTimestamp.get()) < CACHE_TTL_MS) {
            log.debug("💱 Using cached exchange rates (age: {}s)",
                    (now - cacheTimestamp.get()) / 1000);
            return cached;
        }

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(RATE_URL, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null || !"success".equals(body.get("result"))) {
                log.warn("⚠️ Exchange rate API returned non-success — using fallback rates");
                return FALLBACK_RATES;
            }

            Map<String, Object> raw = (Map<String, Object>) body.get("rates");
            Map<String, Double> rates = new HashMap<>();
            raw.forEach((k, v) -> rates.put(k, ((Number) v).doubleValue()));

            // Update cache
            cachedRates.set(rates);
            cacheTimestamp.set(now);

            log.info("💱 Live exchange rates fetched successfully " +
                    "(GHS={}, NGN={}, USD=1.0)",
                    rates.get("GHS"), rates.get("NGN"));

            return rates;

        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch live exchange rates: {} — using fallback", e.getMessage());
            return FALLBACK_RATES;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}