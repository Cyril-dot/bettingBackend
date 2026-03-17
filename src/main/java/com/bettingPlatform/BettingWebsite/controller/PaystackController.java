package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.UserPrincipal;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.service.IpCurrencyResolver;
import com.bettingPlatform.BettingWebsite.service.PaystackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Payment Controller — Paystack VIP subscription flow.
 *
 * Flow:
 *   1. GET  /vip/price         → frontend fetches user's local price (auto-detected from IP)
 *                                 Optional ?currency=NGN lets user manually override
 *   2. POST /initiate          → user confirms payment; currency auto-detected from IP
 *                                 Optional body { "currency": "NGN" } for manual override
 *   3. GET  /verify/{ref}      → user returns from Paystack redirect, we verify + activate VIP
 *   4. POST /webhook           → Paystack server-to-server confirmation (no auth required)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaystackController {

    private final PaystackService     paystackService;
    private final IpCurrencyResolver  ipCurrencyResolver;  // ← IP → currency detection


    // ─────────────────────────────────────────────────────────────
    // Step 0 — Get VIP price (auto-converted to user's local currency)
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/payment/vip/price
     * GET /api/v1/payment/vip/price?currency=NGN   ← user manually overrides currency
     *
     * Called by the frontend BEFORE showing the payment screen.
     * Detects the user's country from their IP and returns the VIP price
     * already converted to their local currency (GHS, NGN, or USD).
     *
     * Also returns all three currency options so the frontend can render
     * a currency switcher if desired.
     *
     * Response shape:
     * {
     *   "baseCurrency":  "USD",
     *   "basePrice":     10.00,
     *   "userCurrency":  "GHS",
     *   "userPrice":     155.00,
     *   "exchangeRate":  15.5,
     *   "description":   "24-hour VIP access",
     *   "options": [
     *     { "currency": "GHS", "price": 155.00,   "label": "Pay in Ghana Cedis (GHS)",    "selected": true  },
     *     { "currency": "NGN", "price": 16000.00, "label": "Pay in Nigerian Naira (NGN)", "selected": false },
     *     { "currency": "USD", "price": 10.00,    "label": "Pay in US Dollars (USD)",     "selected": false }
     *   ]
     * }
     */
    @GetMapping("/vip/price")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVipPrice(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request,
            @RequestParam(required = false) String currency) {

        String clientIp = ipCurrencyResolver.extractIp(request);

        log.info("[PAYMENT][VIP] Price requested → userId={} ip={} currencyOverride={}",
                userPrincipal.getUserId(), clientIp, currency);

        try {
            Map<String, Object> price = paystackService.getVipPriceForUser(clientIp, currency);

            log.info("[PAYMENT][VIP] Price returned → userId={} userCurrency={} userPrice={} exchangeRate={}",
                    userPrincipal.getUserId(),
                    price.get("userCurrency"),
                    price.get("userPrice"),
                    price.get("exchangeRate"));

            return ResponseEntity.ok(ApiResponse.success("VIP price", price));

        } catch (RuntimeException e) {
            log.warn("[PAYMENT][VIP] Price not configured → reason={}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[PAYMENT][VIP] Failed to fetch price → reason={}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch VIP price"));
        }
    }


    // ─────────────────────────────────────────────────────────────
    // Step 1 — Initiate Payment
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/payment/initiate
     *
     * Authenticated user initiates a VIP subscription payment.
     * Currency is AUTO-DETECTED from the user's IP address.
     *
     * Optional request body for manual currency override:
     * { "currency": "NGN" }
     *
     * If the body is absent or the currency field is null/invalid,
     * the IP-detected currency is used.
     *
     * The service converts the admin's base price to the user's currency
     * using live exchange rates, then initializes the Paystack transaction.
     */
    @PostMapping("/initiate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<InitiatePaymentResponse>> initiatePayment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, String> body) {

        String clientIp         = ipCurrencyResolver.extractIp(request);
        String currencyOverride = body != null ? body.get("currency") : null;

        log.info("[PAYMENT][INITIATE] Payment initiation requested → userId={} email={} ip={} currencyOverride={}",
                userPrincipal.getUserId(), userPrincipal.getEmail(), clientIp, currencyOverride);

        try {
            InitiatePaymentResponse response =
                    paystackService.initiatePayment(userPrincipal, clientIp, currencyOverride);

            log.info("[PAYMENT][INITIATE] Payment initiated → userId={} ref={} amount={} {}",
                    userPrincipal.getUserId(), response.getReference(),
                    response.getAmount(), response.getCurrency());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Payment initiated", response));

        } catch (RuntimeException e) {
            log.warn("[PAYMENT][INITIATE] Initiation rejected → userId={} reason={}",
                    userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[PAYMENT][INITIATE] Unexpected error → userId={} reason={}",
                    userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Payment initialization failed. Try again."));
        }
    }


    // ─────────────────────────────────────────────────────────────
    // Step 2 — Verify Payment
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/payment/verify/{reference}
     * Called after user returns from Paystack redirect.
     * Verifies the transaction with Paystack and activates VIP if successful.
     */
    @GetMapping("/verify/{reference}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentVerificationResponse>> verifyPayment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String reference) {

        log.info("[PAYMENT][VERIFY] Verification requested → userId={} ref={}",
                userPrincipal.getUserId(), reference);

        try {
            PaymentVerificationResponse response = paystackService.verifyPayment(reference);

            if (response.isVipActivated()) {
                log.info("[PAYMENT][VERIFY] Payment verified + VIP activated → userId={} ref={} expiresAt={}",
                        userPrincipal.getUserId(), reference, response.getVipExpiresAt());
            } else {
                log.warn("[PAYMENT][VERIFY] Payment failed verification → userId={} ref={} status={}",
                        userPrincipal.getUserId(), reference, response.getStatus());
            }

            return ResponseEntity.ok(ApiResponse.success("Payment verification complete", response));

        } catch (RuntimeException e) {
            log.warn("[PAYMENT][VERIFY] Verification rejected → userId={} ref={} reason={}",
                    userPrincipal.getUserId(), reference, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[PAYMENT][VERIFY] Unexpected error → userId={} ref={} reason={}",
                    userPrincipal.getUserId(), reference, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Payment verification failed. Contact support."));
        }
    }


    // ─────────────────────────────────────────────────────────────
    // Step 3 — Webhook (no auth — Paystack calls this directly)
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/payment/webhook
     * Paystack server-to-server event notification.
     * Must be excluded from Spring Security auth filter.
     * Signature is validated inside the service using HMAC-SHA512.
     * Always returns 200 — Paystack retries on any non-200 response.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader("x-paystack-signature") String signature) {

        String event = payload.get("event") != null
                ? payload.get("event").toString() : "unknown";

        log.info("[PAYMENT][WEBHOOK] Received → event={}", event);

        try {
            paystackService.handleWebhook(payload, signature);
            log.info("[PAYMENT][WEBHOOK] Processed successfully → event={}", event);
            return ResponseEntity.ok().build();

        } catch (RuntimeException e) {
            log.warn("[PAYMENT][WEBHOOK] Rejected → event={} reason={}", event, e.getMessage());
            return ResponseEntity.ok().build(); // Always 200 to prevent Paystack retries

        } catch (Exception e) {
            log.error("[PAYMENT][WEBHOOK] Unexpected error → event={} reason={}", event, e.getMessage(), e);
            return ResponseEntity.ok().build();
        }
    }


    // ─────────────────────────────────────────────────────────────
    // User — VIP Status & Payment History
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/payment/vip/status
     * Returns current VIP subscription status, hours remaining, and base price info.
     */
    @GetMapping("/vip/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVipStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("[PAYMENT][VIP] Status requested → userId={}", userPrincipal.getUserId());

        try {
            Map<String, Object> status = paystackService.getVipStatus(userPrincipal);

            log.info("[PAYMENT][VIP] Status returned → userId={} isVip={} hoursRemaining={}",
                    userPrincipal.getUserId(),
                    status.get("isVip"),
                    status.getOrDefault("hoursRemaining", 0));

            return ResponseEntity.ok(ApiResponse.success("VIP status", status));

        } catch (RuntimeException e) {
            log.warn("[PAYMENT][VIP] User not found → userId={} reason={}",
                    userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[PAYMENT][VIP] Failed to fetch VIP status → userId={} reason={}",
                    userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch VIP status"));
        }
    }

    /**
     * GET /api/v1/payment/history
     * Returns the authenticated user's full payment history.
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<PaymentHistoryResponse>>> getPaymentHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("[PAYMENT][HISTORY] History requested → userId={}", userPrincipal.getUserId());

        try {
            List<PaymentHistoryResponse> history = paystackService.getPaymentHistory(userPrincipal);

            log.info("[PAYMENT][HISTORY] History returned → userId={} count={}",
                    userPrincipal.getUserId(), history.size());

            return ResponseEntity.ok(ApiResponse.success("Payment history", history));

        } catch (RuntimeException e) {
            log.warn("[PAYMENT][HISTORY] User not found → userId={} reason={}",
                    userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[PAYMENT][HISTORY] Failed to fetch history → userId={} reason={}",
                    userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch payment history"));
        }
    }
}