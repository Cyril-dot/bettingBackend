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
    private final CurrencyConverter    currencyConverter;    // ← live conversion
    private final IpCurrencyResolver   ipCurrencyResolver;  // ← IP → currency

    @Value("${paystack.secret.key}")
    private String paystackSecretKey;

    @Value("${paystack.base.url}")
    private String paystackBaseUrl;

    @Value("${paystack.callback.url}")
    private String callbackUrl;


    // ════════════════════════════════════════════════════════════════
    // STEP 1 — User initiates payment
    //
    // Flow:
    //   1. Detect user's currency from IP (auto) or manual override
    //   2. Load admin's base price (any currency, e.g. USD)
    //   3. Convert base price → user's currency using live rates
    //   4. Initialize Paystack transaction
    //   5. Save Payment record and return authorization URL
    // ════════════════════════════════════════════════════════════════

    /**
     * @param userPrincipal  authenticated user
     * @param clientIp       raw IP extracted from the HTTP request (from controller)
     * @param currencyOverride  optional manual currency override from frontend (nullable)
     *                          Must be "GHS", "NGN", or "USD" — ignored if invalid
     */
    public InitiatePaymentResponse initiatePayment(UserPrincipal userPrincipal,
                                                   String clientIp,
                                                   String currencyOverride) {

        // ── 1. Resolve user's currency (IP auto-detect + optional override) ──
        String userCurrency = ipCurrencyResolver
                .resolveCurrencyWithOverride(clientIp, currencyOverride);

        log.info("💳 Payment init — IP: {}, detected currency: {}, override: {}",
                clientIp, userCurrency, currencyOverride);

        // ── 2. Load user ──────────────────────────────────────────────────────
        User user = userRepo.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Block if already VIP
        vipSubscriptionRepo.findByUserAndActiveTrue(user).ifPresent(sub -> {
            throw new RuntimeException(
                    "You already have an active VIP subscription expiring at " + sub.getExpiresAt());
        });

        // ── 3. Load admin price + convert to user's currency ──────────────────
        VipPrice vipPrice = vipPriceRepo.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("VIP price not configured yet"));

        double baseAmount    = vipPrice.getPrice();       // e.g. 10.00
        String baseCurrency  = vipPrice.getCurrency();    // e.g. "USD"

        // Live cross-conversion: USD → GHS / NGN / USD
        double chargeAmount  = currencyConverter.convert(baseAmount, baseCurrency, userCurrency);
        double displayRate   = currencyConverter.getRate(baseCurrency, userCurrency);

        // Paystack expects the amount in the smallest unit (pesewas / kobo / cents)
        int amountSmallestUnit = (int) Math.round(chargeAmount * 100);

        log.info("💱 Price conversion: {} {} → {} {}  (rate: 1 {} = {} {})",
                baseAmount, baseCurrency,
                chargeAmount, userCurrency,
                baseCurrency, displayRate, userCurrency);

        // ── 4. Build reference + Paystack payload ─────────────────────────────
        String reference = "VIP_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16).toUpperCase();

        Map<String, Object> paystackBody = new HashMap<>();
        paystackBody.put("email",        user.getEmail());
        paystackBody.put("amount",       amountSmallestUnit);
        paystackBody.put("currency",     userCurrency);
        paystackBody.put("reference",    reference);
        paystackBody.put("callback_url", callbackUrl);
        paystackBody.put("metadata", Map.of(
                "userId",           user.getId().toString(),
                "purpose",          "VIP_SUBSCRIPTION",
                "userName",         user.getFullName(),
                "baseCurrency",     baseCurrency,
                "baseAmount",       baseAmount,
                "userCurrency",     userCurrency,
                "chargeAmount",     chargeAmount,
                "exchangeRate",     displayRate,
                "clientIp",         clientIp != null ? clientIp : "unknown"
        ));

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paystackBody, headers);

        // ── 5. Call Paystack + save Payment record ────────────────────────────
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    paystackBaseUrl + "/transaction/initialize", entity, Map.class);

            Map responseBody = response.getBody();
            if (responseBody == null || !(Boolean) responseBody.get("status")) {
                throw new RuntimeException("Paystack initialization failed");
            }

            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            String authorizationUrl  = (String) data.get("authorization_url");
            String accessCode        = (String) data.get("access_code");

            Payment payment = Payment.builder()
                    .user(user)
                    .reference(reference)
                    .amount(chargeAmount)
                    .currency(userCurrency)
                    .email(user.getEmail())
                    .purpose(PaymentPurpose.VIP_SUBSCRIPTION)
                    .status(PaymentStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            paymentRepo.save(payment);

            log.info("✅ Payment initialized — User: {}, Ref: {}, Charge: {} {} " +
                            "(base: {} {}, rate: 1 {} = {} {})",
                    user.getEmail(), reference,
                    chargeAmount, userCurrency,
                    baseAmount, baseCurrency,
                    baseCurrency, displayRate, userCurrency);

            return InitiatePaymentResponse.builder()
                    .reference(reference)
                    .authorizationUrl(authorizationUrl)
                    .accessCode(accessCode)
                    .amount(chargeAmount)
                    .currency(userCurrency)
                    .email(user.getEmail())
                    .build();

        } catch (Exception e) {
            log.error("❌ Paystack init failed: {}", e.getMessage());
            throw new RuntimeException("Payment initialization failed. Please try again.");
        }
    }


    // ════════════════════════════════════════════════════════════════
    // STEP 2 — Verify payment after Paystack redirects user back
    // ════════════════════════════════════════════════════════════════

    public PaymentVerificationResponse verifyPayment(String reference) {

        // Idempotency: already verified — return cached result
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
                .orElseThrow(() -> new RuntimeException("Payment record not found: " + reference));

        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    paystackBaseUrl + "/transaction/verify/" + reference,
                    HttpMethod.GET, entity, Map.class);

            Map responseBody = response.getBody();
            if (responseBody == null || !(Boolean) responseBody.get("status")) {
                throw new RuntimeException("Paystack verification call failed");
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
            log.error("❌ Verification error for ref {}: {}", reference, e.getMessage());
            throw new RuntimeException("Payment verification failed. Contact support.");
        }
    }


    // ════════════════════════════════════════════════════════════════
    // STEP 3 — Paystack Webhook (charge.success)
    // ════════════════════════════════════════════════════════════════

    public void handleWebhook(Map<String, Object> payload, String signature) {
        if (!isValidSignature(payload, signature)) {
            log.warn("⚠️ Invalid Paystack webhook signature — rejecting");
            throw new RuntimeException("Invalid webhook signature");
        }

        String event = (String) payload.get("event");
        log.info("📩 Paystack webhook received: {}", event);

        if ("charge.success".equals(event)) {
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String reference = (String) data.get("reference");

            // Idempotency check
            if (paymentRepo.existsByReferenceAndStatus(reference, PaymentStatus.SUCCESS)) {
                log.info("⏩ Webhook: {} already processed — skipping", reference);
                return;
            }

            try {
                verifyPayment(reference);
                log.info("✅ Webhook processed for reference: {}", reference);
            } catch (Exception e) {
                log.error("❌ Webhook processing failed for ref {}: {}", reference, e.getMessage());
            }
        }
    }


    // ════════════════════════════════════════════════════════════════
    // PUBLIC QUERIES
    // ════════════════════════════════════════════════════════════════

    /**
     * Returns the VIP price converted to the user's local currency (detected from IP).
     * Also returns all three Paystack options so the frontend can show a currency switcher.
     *
     * @param clientIp        user's real IP (from controller via ipCurrencyResolver.extractIp)
     * @param currencyOverride optional manual override ("GHS", "NGN", "USD")
     */
    public Map<String, Object> getVipPriceForUser(String clientIp, String currencyOverride) {
        VipPrice price = vipPriceRepo.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("VIP price not configured"));

        double baseAmount   = price.getPrice();
        String baseCurrency = price.getCurrency();

        // Detect user's currency
        String userCurrency = ipCurrencyResolver
                .resolveCurrencyWithOverride(clientIp, currencyOverride);

        double userPrice    = currencyConverter.convert(baseAmount, baseCurrency, userCurrency);
        double displayRate  = currencyConverter.getRate(baseCurrency, userCurrency);

        // Pre-compute all three Paystack currency options
        List<Map<String, Object>> options = IpCurrencyResolver.PAYSTACK_CURRENCIES
                .stream()
                .map(cur -> {
                    double converted = currencyConverter.convert(baseAmount, baseCurrency, cur);
                    return (Map<String, Object>) new HashMap<String, Object>(Map.of(
                            "currency", cur,
                            "price",    converted,
                            "label",    currencyLabel(cur),
                            "selected", cur.equals(userCurrency)
                    ));
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("baseCurrency",  baseCurrency);          // admin's currency, e.g. "USD"
        result.put("basePrice",     baseAmount);             // admin's price, e.g. 10.00
        result.put("userCurrency",  userCurrency);           // auto-detected, e.g. "GHS"
        result.put("userPrice",     userPrice);              // converted price for user
        result.put("exchangeRate",  displayRate);            // 1 baseCurrency = X userCurrency
        result.put("description",   price.getDescription());
        result.put("options",       options);                // all three currency options

        log.info("💰 VIP price served — IP: {}, currency: {}, price: {} {}",
                clientIp, userCurrency, userPrice, userCurrency);

        return result;
    }

    /**
     * Returns the current user's VIP subscription status.
     */
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

        vipPriceRepo.findByActiveTrue().ifPresent(p -> {
            status.put("basePrice",    p.getPrice());
            status.put("baseCurrency", p.getCurrency());
            status.put("description",  p.getDescription());
        });

        return status;
    }

    /**
     * Returns the authenticated user's payment history.
     */
    public List<PaymentHistoryResponse> getPaymentHistory(UserPrincipal userPrincipal) {
        User user = userRepo.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return paymentRepo.findByUser(user)
                .stream()
                .map(this::mapPayment)
                .collect(Collectors.toList());
    }


    // ════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════

    /**
     * Activates (or renews) the user's VIP for 24 hours.
     */
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

        log.info("👑 VIP activated for {} — expires at {}", user.getEmail(), expiresAt);
        return expiresAt;
    }

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
            for (byte b : hashBytes) hexHash.append(String.format("%02x", b));

            return hexHash.toString().equals(signature);
        } catch (Exception e) {
            log.error("❌ Signature validation error: {}", e.getMessage());
            return false;
        }
    }

    private String currencyLabel(String currency) {
        return switch (currency) {
            case "GHS" -> "Pay in Ghana Cedis (GHS)";
            case "NGN" -> "Pay in Nigerian Naira (NGN)";
            case "USD" -> "Pay in US Dollars (USD)";
            default    -> "Pay in " + currency;
        };
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