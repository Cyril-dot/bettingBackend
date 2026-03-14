package com.bettingPlatform.BettingWebsite.service;

import com.bettingPlatform.BettingWebsite.Config.Security.UserPrincipal;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.entity.*;
import com.bettingPlatform.BettingWebsite.entity.repos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaystackService {

    private final PaymentRepo          paymentRepo;
    private final UserRepo             userRepo;
    private final VipPriceRepo         vipPriceRepo;
    private final VipSubscriptionRepo  vipSubscriptionRepo;
    private final RestTemplate         restTemplate;

    @Value("${paystack.secret.key}")
    private String paystackSecretKey;

    @Value("${paystack.base.url}")
    private String paystackBaseUrl;

    @Value("${paystack.callback.url}")
    private String callbackUrl;

    // ── Exchange rate constants ───────────────────────────────────
    private static final String GHS = "GHS";
    private static final String NGN = "NGN";

    // Free exchange rate API — no key required
    private static final String EXCHANGE_RATE_URL =
            "https://open.er-api.com/v6/latest/GHS";

    // Fallback rate used if the live API is unreachable
    private static final double FALLBACK_GHS_TO_NGN = 45.0;

    // ── Simple in-memory cache (rate + timestamp) ─────────────────
    // Caches for 1 hour so we don't hammer the free API on every payment
    private static final long   CACHE_TTL_MS     = 60 * 60 * 1000L; // 1 hour
    private final AtomicReference<Double> cachedRate      = new AtomicReference<>(null);
    private final AtomicLong              cacheTimestamp   = new AtomicLong(0);


    // ── STEP 1: User initiates payment ───────────────────────────
    // currency: "GHS" or "NGN" — sent by frontend based on user's selection
    public InitiatePaymentResponse initiatePayment(UserPrincipal userPrincipal,
                                                   String currency) {

        // Normalise — default to GHS if invalid
        String selectedCurrency = NGN.equalsIgnoreCase(currency) ? NGN : GHS;

        User user = userRepo.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Block if already VIP
        vipSubscriptionRepo.findByUserAndActiveTrue(user).ifPresent(sub -> {
            throw new RuntimeException(
                    "You already have an active VIP subscription expiring at "
                            + sub.getExpiresAt());
        });

        // Base price always stored in GHS
        VipPrice vipPrice = vipPriceRepo.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("VIP price not configured yet"));

        double baseAmountGhs = vipPrice.getPrice();

        // Get live rate and convert
        double liveRate     = fetchLiveGhsToNgnRate();
        double chargeAmount = convertAmount(baseAmountGhs, selectedCurrency, liveRate);

        // Paystack expects amount in smallest unit (pesewas / kobo)
        int amountInSmallestUnit = (int) Math.round(chargeAmount * 100);

        String reference = "VIP_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16).toUpperCase();

        Map<String, Object> paystackBody = new HashMap<>();
        paystackBody.put("email",        user.getEmail());
        paystackBody.put("amount",       amountInSmallestUnit);
        paystackBody.put("currency",     selectedCurrency);
        paystackBody.put("reference",    reference);
        paystackBody.put("callback_url", callbackUrl);
        paystackBody.put("metadata", Map.of(
                "userId",           user.getId().toString(),
                "purpose",          "VIP_SUBSCRIPTION",
                "userName",         user.getFullName(),
                "baseCurrency",     GHS,
                "baseAmount",       baseAmountGhs,
                "selectedCurrency", selectedCurrency,
                "chargeAmount",     chargeAmount,
                "exchangeRate",     liveRate
        ));

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paystackBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    paystackBaseUrl + "/transaction/initialize",
                    entity,
                    Map.class);

            Map responseBody = response.getBody();
            if (responseBody == null || !(Boolean) responseBody.get("status")) {
                throw new RuntimeException("Paystack initialization failed");
            }

            Map<String, Object> data    = (Map<String, Object>) responseBody.get("data");
            String authorizationUrl     = (String) data.get("authorization_url");
            String accessCode           = (String) data.get("access_code");

            // Save payment with actual charge currency + amount
            Payment payment = Payment.builder()
                    .user(user)
                    .reference(reference)
                    .amount(chargeAmount)
                    .currency(selectedCurrency)
                    .email(user.getEmail())
                    .purpose(PaymentPurpose.VIP_SUBSCRIPTION)
                    .status(PaymentStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            paymentRepo.save(payment);

            log.info("💳 Payment initiated — User: {}, Ref: {}, Charge: {} {} " +
                            "(base: {} GHS, live rate: 1 GHS = {} NGN)",
                    user.getEmail(), reference,
                    chargeAmount, selectedCurrency,
                    baseAmountGhs, liveRate);

            return InitiatePaymentResponse.builder()
                    .reference(reference)
                    .authorizationUrl(authorizationUrl)
                    .accessCode(accessCode)
                    .amount(chargeAmount)
                    .currency(selectedCurrency)
                    .email(user.getEmail())
                    .build();

        } catch (Exception e) {
            log.error("❌ Paystack init failed: {}", e.getMessage());
            throw new RuntimeException("Payment initialization failed. Try again.");
        }
    }


    // ── STEP 2: Verify payment after redirect ────────────────────
    public PaymentVerificationResponse verifyPayment(String reference) {

        // Already verified — return cached result
        Optional<Payment> existingOpt = paymentRepo.findByReference(reference);
        if (existingOpt.isPresent()
                && existingOpt.get().getStatus() == PaymentStatus.SUCCESS) {
            log.info("⏩ Payment {} already verified — returning cached result", reference);
            User user = existingOpt.get().getUser();
            Optional<VipSubscription> sub = vipSubscriptionRepo.findByUserAndActiveTrue(user);
            return PaymentVerificationResponse.builder()
                    .reference(reference)
                    .status("success")
                    .amount(existingOpt.get().getAmount())
                    .currency(existingOpt.get().getCurrency())
                    .message("VIP already active.")
                    .vipActivated(true)
                    .vipExpiresAt(sub.map(VipSubscription::getExpiresAt).orElse(null))
                    .build();
        }

        Payment payment = paymentRepo.findByReference(reference)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    paystackBaseUrl + "/transaction/verify/" + reference,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            Map responseBody = response.getBody();
            if (responseBody == null || !(Boolean) responseBody.get("status")) {
                throw new RuntimeException("Paystack verification failed");
            }

            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            String paystackStatus    = (String) data.get("status");
            String gatewayResponse   = (String) data.get("gateway_response");
            String channel           = (String) data.get("channel");
            String paystackId        = String.valueOf(data.get("id"));

            payment.setPaystackId(paystackId);
            payment.setGatewayResponse(gatewayResponse);
            payment.setChannel(channel);
            payment.setUpdatedAt(LocalDateTime.now());

            if ("success".equals(paystackStatus)) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(LocalDateTime.now());
                paymentRepo.save(payment);

                log.info("✅ Payment verified — Ref: {}, User: {}, Amount: {} {}",
                        reference, payment.getUser().getEmail(),
                        payment.getAmount(), payment.getCurrency());

                LocalDateTime expiresAt = activateVip(payment.getUser());

                return PaymentVerificationResponse.builder()
                        .reference(reference)
                        .status("success")
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .message("Payment successful! VIP activated for 24 hours.")
                        .vipActivated(true)
                        .vipExpiresAt(expiresAt)
                        .build();

            } else {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepo.save(payment);
                log.warn("❌ Payment failed — Ref: {}, Status: {}", reference, paystackStatus);

                return PaymentVerificationResponse.builder()
                        .reference(reference)
                        .status("failed")
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .message("Payment failed. Please try again.")
                        .vipActivated(false)
                        .build();
            }

        } catch (Exception e) {
            log.error("❌ Verification error for {}: {}", reference, e.getMessage());
            throw new RuntimeException("Payment verification failed. Contact support.");
        }
    }


    // ── STEP 3: Paystack Webhook ──────────────────────────────────
    public void handleWebhook(Map<String, Object> payload, String signature) {
        if (!isValidSignature(payload, signature)) {
            log.warn("⚠️ Invalid Paystack webhook signature");
            throw new RuntimeException("Invalid signature");
        }

        String event = (String) payload.get("event");
        log.info("📩 Paystack webhook received: {}", event);

        if ("charge.success".equals(event)) {
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String reference = (String) data.get("reference");

            if (paymentRepo.existsByReferenceAndStatus(reference, PaymentStatus.SUCCESS)) {
                log.info("⏩ Webhook: {} already processed", reference);
                return;
            }

            try {
                verifyPayment(reference);
                log.info("✅ Webhook processed for reference: {}", reference);
            } catch (Exception e) {
                log.error("❌ Webhook processing failed: {}", e.getMessage());
            }
        }
    }


    // ── VIP Activation ────────────────────────────────────────────
    private LocalDateTime activateVip(User user) {
        LocalDateTime now       = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(24);

        VipSubscription subscription = vipSubscriptionRepo
                .findByUserAndActiveTrue(user)
                .orElse(VipSubscription.builder().user(user).build());

        subscription.setActivatedAt(now);
        subscription.setExpiresAt(expiresAt);
        subscription.setActive(true);
        vipSubscriptionRepo.save(subscription);

        log.info("👑 VIP activated for {} — expires: {}", user.getEmail(), expiresAt);
        return expiresAt;
    }


    // ── Get VIP status ────────────────────────────────────────────
    public Map<String, Object> getVipStatus(UserPrincipal userPrincipal) {
        User user = userRepo.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<VipSubscription> sub = vipSubscriptionRepo.findByUserAndActiveTrue(user);

        Map<String, Object> status = new HashMap<>();
        status.put("isVip", sub.isPresent());

        sub.ifPresent(s -> {
            status.put("activatedAt",    s.getActivatedAt());
            status.put("expiresAt",      s.getExpiresAt());
            long hoursLeft = java.time.Duration.between(
                    LocalDateTime.now(), s.getExpiresAt()).toHours();
            status.put("hoursRemaining", Math.max(hoursLeft, 0));
        });

        vipPriceRepo.findByActiveTrue().ifPresent(price -> {
            status.put("price",       price.getPrice());
            status.put("currency",    price.getCurrency());
            status.put("description", price.getDescription());
        });

        return status;
    }


    // ── Get VIP price in both currencies (with live rate) ─────────
    // Frontend calls this to show both GHS and NGN options to the user
    public Map<String, Object> getVipPrice() {
        VipPrice price = vipPriceRepo.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("VIP price not configured"));

        double ghsPrice  = price.getPrice();
        double liveRate  = fetchLiveGhsToNgnRate();
        double ngnPrice  = convertAmount(ghsPrice, NGN, liveRate);

        Map<String, Object> result = new HashMap<>();
        result.put("baseCurrency", GHS);
        result.put("basePrice",    ghsPrice);
        result.put("description",  price.getDescription());
        result.put("exchangeRate", liveRate);
        result.put("rateSource",   "live");

        // Both options for the frontend to render
        result.put("options", List.of(
                Map.of("currency", GHS, "price", ghsPrice, "label", "Pay in Ghana Cedis (GHS)"),
                Map.of("currency", NGN, "price", ngnPrice, "label", "Pay in Nigerian Naira (NGN)")
        ));

        return result;
    }


    // ── Get payment history ───────────────────────────────────────
    public List<PaymentHistoryResponse> getPaymentHistory(UserPrincipal userPrincipal) {
        User user = userRepo.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return paymentRepo.findByUser(user)
                .stream()
                .map(this::mapPayment)
                .collect(Collectors.toList());
    }


    // ══════════════════════════════════════════════════════════════
    // EXCHANGE RATE — live fetch with 1-hour cache + fallback
    // ══════════════════════════════════════════════════════════════

    /**
     * Fetches the live GHS → NGN rate from open.er-api.com.
     * - Caches the result for 1 hour to avoid hammering the free API.
     * - Falls back to FALLBACK_GHS_TO_NGN (45.0) if the API is unreachable.
     * - No API key required for open.er-api.com free tier.
     */
    @SuppressWarnings("unchecked")
    private double fetchLiveGhsToNgnRate() {
        long now = System.currentTimeMillis();

        // Return cached rate if still fresh
        Double cached = cachedRate.get();
        if (cached != null && (now - cacheTimestamp.get()) < CACHE_TTL_MS) {
            log.debug("💱 Using cached GHS→NGN rate: {}", cached);
            return cached;
        }

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    EXCHANGE_RATE_URL, Map.class);

            Map body = response.getBody();
            if (body == null || !"success".equals(body.get("result"))) {
                log.warn("⚠️ Exchange rate API returned non-success — using fallback rate {}",
                        FALLBACK_GHS_TO_NGN);
                return FALLBACK_GHS_TO_NGN;
            }

            Map<String, Object> rates = (Map<String, Object>) body.get("rates");
            Object ngnRateObj = rates.get("NGN");

            if (ngnRateObj == null) {
                log.warn("⚠️ NGN not found in exchange rate response — using fallback {}",
                        FALLBACK_GHS_TO_NGN);
                return FALLBACK_GHS_TO_NGN;
            }

            double liveRate = ((Number) ngnRateObj).doubleValue();

            // Cache it
            cachedRate.set(liveRate);
            cacheTimestamp.set(now);

            log.info("💱 Live exchange rate fetched: 1 GHS = {} NGN", liveRate);
            return liveRate;

        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch live exchange rate: {} — using fallback {}",
                    e.getMessage(), FALLBACK_GHS_TO_NGN);
            return FALLBACK_GHS_TO_NGN;
        }
    }

    /**
     * Converts a GHS amount to the target currency using the provided rate.
     * GHS → GHS: no change.
     * GHS → NGN: multiply by rate.
     */
    private double convertAmount(double ghsAmount, String targetCurrency, double rate) {
        if (NGN.equalsIgnoreCase(targetCurrency)) {
            double converted = Math.round(ghsAmount * rate * 100.0) / 100.0;
            log.debug("💱 {} GHS × {} = {} NGN", ghsAmount, rate, converted);
            return converted;
        }
        return ghsAmount;
    }


    // ── HELPERS ───────────────────────────────────────────────────

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(paystackSecretKey);
        return headers;
    }

    private boolean isValidSignature(Map<String, Object> payload, String signature) {
        try {
            String payloadString = new com.fasterxml.jackson.databind
                    .ObjectMapper().writeValueAsString(payload);

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    paystackSecretKey.getBytes(), "HmacSHA512"));

            byte[] hashBytes = mac.doFinal(payloadString.getBytes());
            StringBuilder hexHash = new StringBuilder();
            for (byte b : hashBytes) {
                hexHash.append(String.format("%02x", b));
            }
            return hexHash.toString().equals(signature);
        } catch (Exception e) {
            log.error("❌ Signature validation error: {}", e.getMessage());
            return false;
        }
    }

    private PaymentHistoryResponse mapPayment(Payment p) {
        return PaymentHistoryResponse.builder()
                .id(p.getId())
                .reference(p.getReference())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .purpose(p.getPurpose())
                .channel(p.getChannel())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}