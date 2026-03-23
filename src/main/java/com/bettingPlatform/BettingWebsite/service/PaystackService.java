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
    private final CurrencyConverter    currencyConverter;
    private final IpCurrencyResolver   ipCurrencyResolver;

    @Value("${paystack.secret.key}")
    private String paystackSecretKey;

    @Value("${paystack.base.url}")
    private String paystackBaseUrl;

    @Value("${paystack.callback.url}")
    private String callbackUrl;


    // ════════════════════════════════════════════════════════════════
    // STEP 1 — User initiates payment
    // ════════════════════════════════════════════════════════════════

    public InitiatePaymentResponse initiatePayment(UserPrincipal userPrincipal,
                                                   String clientIp,
                                                   String currencyOverride) {

        String userCurrency = ipCurrencyResolver
                .resolveCurrencyWithOverride(clientIp, currencyOverride);

        log.info("💳 Payment init — IP: {}, detected currency: {}, override: {}",
                clientIp, userCurrency, currencyOverride);

        User user = userRepo.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Block only if the user has an active, non-expired subscription.
        vipSubscriptionRepo.findActiveAndNotExpired(user, LocalDateTime.now()).ifPresent(sub -> {
            throw new RuntimeException(
                    "You already have an active VIP subscription expiring at " + sub.getExpiresAt());
        });

        VipPrice vipPrice = vipPriceRepo.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("VIP price not configured yet"));

        double baseAmount   = vipPrice.getPrice();
        String baseCurrency = vipPrice.getCurrency();

        double chargeAmount    = currencyConverter.convert(baseAmount, baseCurrency, userCurrency);
        double displayRate     = currencyConverter.getRate(baseCurrency, userCurrency);
        int amountSmallestUnit = (int) Math.round(chargeAmount * 100);

        log.info("💱 Price conversion: {} {} → {} {}  (rate: 1 {} = {} {})",
                baseAmount, baseCurrency,
                chargeAmount, userCurrency,
                baseCurrency, displayRate, userCurrency);

        String reference = "VIP_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16).toUpperCase();

        Map<String, Object> paystackBody = new HashMap<>();
        paystackBody.put("email",        user.getEmail());
        paystackBody.put("amount",       amountSmallestUnit);
        paystackBody.put("currency",     userCurrency);
        paystackBody.put("reference",    reference);
        paystackBody.put("callback_url", callbackUrl);
        paystackBody.put("metadata", Map.of(
                "userId",       user.getId().toString(),
                "purpose",      "VIP_SUBSCRIPTION",
                "userName",     user.getFullName(),
                "baseCurrency", baseCurrency,
                "baseAmount",   baseAmount,
                "userCurrency", userCurrency,
                "chargeAmount", chargeAmount,
                "exchangeRate", displayRate,
                "clientIp",     clientIp != null ? clientIp : "unknown"
        ));

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paystackBody, headers);

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
    // STEP 2 — Verify payment (called by frontend redirect OR webhook)
    // ════════════════════════════════════════════════════════════════

    public PaymentVerificationResponse verifyPayment(String reference) {

        // Idempotency: already verified — return cached result.
        Optional<Payment> existingOpt = paymentRepo.findByReference(reference);
        if (existingOpt.isPresent()
                && existingOpt.get().getStatus() == PaymentStatus.SUCCESS) {
            log.info("⏩ Payment {} already verified — returning cached result", reference);
            User user = existingOpt.get().getUser();
            Optional<VipSubscription> sub =
                    vipSubscriptionRepo.findActiveAndNotExpired(user, LocalDateTime.now());
            return PaymentVerificationResponse.builder()
                    .reference(reference)
                    .status("success")
                    .amount(existingOpt.get().getAmount())
                    .currency(existingOpt.get().getCurrency())
                    .message(sub.isPresent() ? "VIP already active." : "VIP subscription has expired.")
                    .vipActivated(sub.isPresent())
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
    //
    // FIX: The old isValidSignature() re-serialised the Map payload with
    // Jackson, which does NOT guarantee key ordering. Paystack signs the
    // raw request body string, so a re-serialised map produces a different
    // HMAC and the check always fails, silently dropping webhooks.
    //
    // The controller must now read the raw body as a String, pass it here
    // for signature validation, AND separately parse it into the Map.
    // See PaystackWebhookController for the updated controller code.
    // ════════════════════════════════════════════════════════════════

    /**
     * @param payload      parsed webhook body (used for event routing only)
     * @param rawBody      the exact bytes Paystack sent — used for HMAC validation
     * @param signature    value of the X-Paystack-Signature header
     */
    public void handleWebhook(Map<String, Object> payload, String rawBody, String signature) {
        if (!isValidSignature(rawBody, signature)) {
            log.warn("⚠️ Invalid Paystack webhook signature — rejecting");
            throw new RuntimeException("Invalid webhook signature");
        }

        String event = (String) payload.get("event");
        log.info("📩 Paystack webhook received: {}", event);

        if ("charge.success".equals(event)) {
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String reference = (String) data.get("reference");

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

    public Map<String, Object> getVipPriceForUser(String clientIp, String currencyOverride) {
        VipPrice price = vipPriceRepo.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("VIP price not configured"));

        double baseAmount   = price.getPrice();
        String baseCurrency = price.getCurrency();

        String userCurrency = ipCurrencyResolver
                .resolveCurrencyWithOverride(clientIp, currencyOverride);

        double userPrice   = currencyConverter.convert(baseAmount, baseCurrency, userCurrency);
        double displayRate = currencyConverter.getRate(baseCurrency, userCurrency);

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
        result.put("baseCurrency", baseCurrency);
        result.put("basePrice",    baseAmount);
        result.put("userCurrency", userCurrency);
        result.put("userPrice",    userPrice);
        result.put("exchangeRate", displayRate);
        result.put("description",  price.getDescription());
        result.put("options",      options);

        log.info("💰 VIP price served — IP: {}, currency: {}, price: {} {}",
                clientIp, userCurrency, userPrice, userCurrency);

        return result;
    }

    /**
     * FIX: Uses findActiveAndNotExpired() so VIP status is always evaluated
     * against the subscription's own expiresAt, never the VipPrice table.
     * Admin changing the price has zero effect on current subscribers.
     */
    public Map<String, Object> getVipStatus(UserPrincipal userPrincipal) {
        User user = userRepo.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<VipSubscription> sub =
                vipSubscriptionRepo.findActiveAndNotExpired(user, LocalDateTime.now());

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

    private LocalDateTime activateVip(User user) {
        LocalDateTime now       = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(24);

        VipSubscription subscription = vipSubscriptionRepo
                .findActiveAndNotExpired(user, now)
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

    /**
     * FIX: Validates against rawBody — the exact string Paystack signed.
     *
     * The old version called ObjectMapper.writeValueAsString(payload) on the
     * already-parsed Map, which reorders keys and produces a different HMAC
     * than what Paystack computed over the original payload bytes.
     * Passing the raw body string from the controller fixes this permanently.
     */
    private boolean isValidSignature(String rawBody, String signature) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    paystackSecretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "HmacSHA512"));

            byte[] hashBytes = mac.doFinal(
                    rawBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder hexHash = new StringBuilder();
            for (byte b : hashBytes) hexHash.append(String.format("%02x", b));

            boolean valid = hexHash.toString().equals(signature);
            if (!valid) {
                log.warn("⚠️ HMAC mismatch — computed: {}, received: {}", hexHash, signature);
            }
            return valid;
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