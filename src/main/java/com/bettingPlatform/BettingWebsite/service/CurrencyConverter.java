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

        // FIX: always fall back to FALLBACK_RATES if rate is missing or zero
        double usdToBase   = safeRate(rates, base);
        double usdToTarget = safeRate(rates, target);

        // Cross-convert:  amount (base) → USD → target
        double inUsd     = amount / usdToBase;
        double converted = round(inUsd * usdToTarget);

        // FIX: use {} placeholders — {:.4f} is Python syntax and breaks SLF4J formatting
        log.debug("💱 {} {} → {} USD → {} {}  (rates: 1 USD = {} {}, 1 USD = {} {})",
                amount, base, String.format("%.4f", inUsd), converted, target,
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
        double usdToBase   = safeRate(rates, base);
        double usdToTarget = safeRate(rates, target);
        return round(usdToTarget / usdToBase);
    }

    // ─────────────────────────────────────────────────────────────
    // Live rate fetch with 1-hour cache + fallback
    // ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Double> fetchRates() {
        long now   = System.currentTimeMillis();
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

            Object rawRates = body.get("rates");
            if (!(rawRates instanceof Map)) {
                log.warn("⚠️ Exchange rate API response missing 'rates' map — using fallback");
                return FALLBACK_RATES;
            }

            Map<String, Object> raw = (Map<String, Object>) rawRates;
            Map<String, Double> rates = new HashMap<>();

            // FIX: safely convert any Number subtype (Integer, Double, Float, Long, BigDecimal)
            // to Double. The API sometimes returns Integer for whole-number rates (e.g. 1),
            // which previously caused incorrect 1.0 defaults when currencies weren't found.
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Number) {
                    rates.put(entry.getKey(), ((Number) val).doubleValue());
                }
            }

            // Sanity check — make sure the currencies we care about are present
            for (String currency : new String[]{"GHS", "NGN", "USD", "EUR", "GBP"}) {
                if (!rates.containsKey(currency)) {
                    log.warn("⚠️ Live rates missing {} — will fall back to hardcoded rate for that currency", currency);
                }
            }

            // Update cache
            cachedRates.set(rates);
            cacheTimestamp.set(now);

            log.info("💱 Live exchange rates fetched — GHS={}, NGN={}, EUR={}, GBP={}",
                    rates.get("GHS"), rates.get("NGN"), rates.get("EUR"), rates.get("GBP"));

            return rates;

        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch live exchange rates: {} — using fallback", e.getMessage());
            return FALLBACK_RATES;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * FIX: Returns the live rate for a currency, falling back to the hardcoded
     * rate if missing from the live map, and finally to 1.0 as last resort.
     * This prevents a missing or zero rate from silently breaking conversions.
     */
    private double safeRate(Map<String, Double> rates, String currency) {
        Double live = rates.get(currency);
        if (live != null && live > 0) return live;
        Double fallback = FALLBACK_RATES.get(currency);
        if (fallback != null && fallback > 0) {
            log.warn("⚠️ Live rate missing for {} — using fallback rate {}", currency, fallback);
            return fallback;
        }
        log.warn("⚠️ No rate found for {} — defaulting to 1.0 (conversion will be wrong)", currency);
        return 1.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}