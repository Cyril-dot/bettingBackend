package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.UserPrincipal;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.service.PaystackService;
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
 *   1. GET  /vip/price    → frontend fetches both GHS + NGN prices to show user
 *   2. POST /initiate     → user picks currency (GHS or NGN) and starts payment
 *   3. GET  /verify/{ref} → user returns from Paystack redirect, we verify
 *   4. POST /webhook      → Paystack server-to-server confirmation (no auth)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaystackController {

    private final PaystackService paystackService;

    // ─────────────────────────────────────────────────────────────
    // Step 1 — Initiate Payment
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/payment/initiate?currency=GHS
     * POST /api/v1/payment/initiate?currency=NGN
     *
     * Authenticated user initiates a VIP subscription payment.
     * The currency param is sent by the frontend based on what the user selects.
     * Defaults to GHS if not provided.
     *
     * The service fetches the live GHS→NGN exchange rate, converts the price,
     * and sends the correct currency + amount to Paystack.
     */
    @PostMapping("/initiate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<InitiatePaymentResponse>> initiatePayment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "GHS") String currency) {

        log.info("[PAYMENT][INITIATE] Payment initiation requested → userId={} email={} currency={}",
                userPrincipal.getUserId(), userPrincipal.getEmail(), currency);

        try {
            InitiatePaymentResponse response = paystackService.initiatePayment(userPrincipal, currency);

            log.info("[PAYMENT][INITIATE] Payment initiated → userId={} ref={} amount={} {}",
                    userPrincipal.getUserId(), response.getReference(),
                    response.getAmount(), response.getCurrency());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Payment initiated", response));

        } catch (RuntimeException e) {
            log.warn("[PAYMENT][INITIATE] Initiation rejected → userId={} reason={}",
                    userPrincipal.getUserId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[PAYMENT][INITIATE] Unexpected error → userId={} reason={}",
                    userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Payment initialization failed. Try again."));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Step 2 — Verify Payment
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/payment/verify/{reference}
     * Called after user returns from Paystack redirect.
     * Verifies the transaction and activates VIP if successful.
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[PAYMENT][VERIFY] Unexpected error → userId={} ref={} reason={}",
                    userPrincipal.getUserId(), reference, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
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
            return ResponseEntity.ok().build(); // Always 200 — Paystack retries on non-200

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
     * Returns current VIP subscription status, hours remaining, and current price.
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
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[PAYMENT][VIP] Failed to fetch VIP status → userId={} reason={}",
                    userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
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
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[PAYMENT][HISTORY] Failed to fetch history → userId={} reason={}",
                    userPrincipal.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch payment history"));
        }
    }

    /**
     * GET /api/v1/payment/vip/price
     *
     * Returns the current VIP price in BOTH currencies with the live exchange rate.
     * Frontend uses this to show both GHS and NGN options to the user before they pay.
     *
     * Response shape:
     * {
     *   "baseCurrency": "GHS",
     *   "basePrice": 50.0,
     *   "description": "VIP subscription - 24 hours",
     *   "exchangeRate": 48.35,
     *   "rateSource": "live",
     *   "options": [
     *     { "currency": "GHS", "price": 50.0,    "label": "Pay in Ghana Cedis (GHS)" },
     *     { "currency": "NGN", "price": 2417.50, "label": "Pay in Nigerian Naira (NGN)" }
     *   ]
     * }
     */
    @GetMapping("/vip/price")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVipPrice(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("[PAYMENT][VIP] Price requested → userId={}", userPrincipal.getUserId());

        try {
            Map<String, Object> price = paystackService.getVipPrice();
            log.info("[PAYMENT][VIP] Price returned → userId={} basePrice={} exchangeRate={}",
                    userPrincipal.getUserId(),
                    price.get("basePrice"),
                    price.get("exchangeRate"));
            return ResponseEntity.ok(ApiResponse.success("VIP price", price));

        } catch (RuntimeException e) {
            log.warn("[PAYMENT][VIP] Price not configured → reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[PAYMENT][VIP] Failed to fetch price → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch VIP price"));
        }
    }
}