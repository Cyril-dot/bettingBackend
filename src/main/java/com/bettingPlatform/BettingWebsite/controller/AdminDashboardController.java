package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.service.AdminDashboardService;
import com.bettingPlatform.BettingWebsite.service.CurrencyConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Admin Dashboard Controller
 *
 * All endpoints require ADMIN role.
 * Structured logging is provided via SLF4J (@Slf4j / Logback).
 *
 * Logging convention:
 *   INFO  – normal operation milestones
 *   WARN  – recoverable anomalies (bad input, not-found, etc.)
 *   ERROR – unexpected failures that need attention
 *
 * VIP Price note:
 *   The admin sets the price in ANY supported currency (USD, GHS, NGN, EUR, GBP).
 *   At payment time, PaystackService automatically converts it to each user's
 *   local currency using live exchange rates (detected from the user's IP address).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;


    // ─────────────────────────────────────────────────────────────────────────
    // VIP Price
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/admin/vip/price
     *
     * Set (or replace) the active VIP subscription price.
     * The price can be set in ANY supported currency — USD, GHS, NGN, EUR, or GBP.
     * Users will automatically be charged in their own local currency at payment time.
     *
     * Request body:
     * {
     *   "price": 10.00,
     *   "currency": "USD",
     *   "description": "24-hour VIP access — unlimited predictions"
     * }
     *
     * Supported currencies: USD · GHS · NGN · EUR · GBP
     * (See CurrencyConverter.SUPPORTED_BASE_CURRENCIES)
     *
     * Response:
     * {
     *   "id": "...",
     *   "price": 10.00,
     *   "currency": "USD",
     *   "description": "24-hour VIP access — unlimited predictions"
     * }
     */
    @PostMapping("/vip/price")
    public ResponseEntity<ApiResponse<VipPriceResponse>> setVipPrice(
            @Valid @RequestBody SetVipPriceRequest request) {

        log.info("[ADMIN][VIP] Setting VIP price → amount={} currency={}",
                request.getPrice(), request.getCurrency());

        try {
            VipPriceResponse response = adminDashboardService.setVipPrice(request);

            log.info("[ADMIN][VIP] Price updated successfully → id={} amount={} {}",
                    response.getId(), response.getPrice(), response.getCurrency());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("VIP price updated", response));

        } catch (RuntimeException e) {
            // Handles unsupported currency, zero price, etc.
            log.warn("[ADMIN][VIP] Invalid VIP price request → amount={} currency={} reason={}",
                    request.getPrice(), request.getCurrency(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[ADMIN][VIP] Failed to set VIP price → reason={}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update VIP price: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/admin/vip/price
     *
     * Retrieve the currently active VIP price as the admin set it (raw — no conversion).
     * For the user-facing price (with live currency conversion), use:
     *   GET /api/v1/payment/vip/price
     */
    @GetMapping("/vip/price")
    public ResponseEntity<ApiResponse<VipPriceResponse>> getCurrentVipPrice() {

        log.info("[ADMIN][VIP] Fetching current VIP price");

        try {
            VipPriceResponse response = adminDashboardService.getCurrentVipPrice();

            log.info("[ADMIN][VIP] Current price fetched → id={} amount={} {}",
                    response.getId(), response.getPrice(), response.getCurrency());

            return ResponseEntity.ok(ApiResponse.success("Current VIP price", response));

        } catch (RuntimeException e) {
            log.warn("[ADMIN][VIP] No active VIP price found → reason={}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No active VIP price configured"));

        } catch (Exception e) {
            log.error("[ADMIN][VIP] Unexpected error fetching VIP price → reason={}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch VIP price"));
        }
    }

    /**
     * GET /api/v1/admin/vip/currencies
     *
     * Returns the list of currencies the admin is allowed to set the price in.
     * Useful for populating the currency dropdown in the admin UI.
     *
     * Response:
     * {
     *   "supportedCurrencies": ["USD", "GHS", "NGN", "EUR", "GBP"],
     *   "paystackCurrencies":  ["GHS", "NGN", "USD"]
     * }
     */
    @GetMapping("/vip/currencies")
    public ResponseEntity<ApiResponse<Object>> getSupportedCurrencies() {

        log.info("[ADMIN][VIP] Supported currencies requested");

        return ResponseEntity.ok(ApiResponse.success("Supported currencies",
                java.util.Map.of(
                        "supportedBaseCurrencies", CurrencyConverter.SUPPORTED_BASE_CURRENCIES,
                        "paystackPaymentCurrencies", com.bettingPlatform.BettingWebsite
                                .service.IpCurrencyResolver.PAYSTACK_CURRENCIES
                )));
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Promos
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/admin/promos
     * Create a new promotion, optionally with an image upload.
     * Accepts multipart/form-data so image + JSON fields are sent together.
     */
    @PostMapping(value = "/promos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PromoResponse>> createPromo(
            @Valid @ModelAttribute CreatePromoRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        String imageInfo = (image != null && !image.isEmpty())
                ? image.getOriginalFilename() + " (" + image.getSize() + " bytes)"
                : "none";

        log.info("[ADMIN][PROMO] Creating promo → title='{}' type={} image={}",
                request.getTitle(), request.getType(), imageInfo);

        try {
            PromoResponse response = adminDashboardService.createPromo(request, image);

            log.info("[ADMIN][PROMO] Promo created successfully → id={} title='{}'",
                    response.getId(), response.getTitle());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Promo created", response));

        } catch (IOException e) {
            log.error("[ADMIN][PROMO] Image upload failed during promo creation → title='{}' reason={}",
                    request.getTitle(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Image upload failed: " + e.getMessage()));

        } catch (Exception e) {
            log.error("[ADMIN][PROMO] Unexpected error creating promo → title='{}' reason={}",
                    request.getTitle(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create promo: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/admin/promos
     * List all currently active promotions (not expired).
     */
    @GetMapping("/promos")
    public ResponseEntity<ApiResponse<List<PromoResponse>>> getActivePromos() {

        log.info("[ADMIN][PROMO] Fetching active promos");

        try {
            List<PromoResponse> promos = adminDashboardService.getActivePromos();

            log.info("[ADMIN][PROMO] Active promos fetched → count={}", promos.size());

            return ResponseEntity.ok(ApiResponse.success("Active promos", promos));

        } catch (Exception e) {
            log.error("[ADMIN][PROMO] Failed to fetch active promos → reason={}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch promos"));
        }
    }

    /**
     * DELETE /api/v1/admin/promos/{id}
     * Delete a promotion and its associated Cloudinary image.
     */
    @DeleteMapping("/promos/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePromo(@PathVariable UUID id) {

        log.info("[ADMIN][PROMO] Deleting promo → id={}", id);

        try {
            adminDashboardService.deletePromo(id);

            log.info("[ADMIN][PROMO] Promo deleted successfully → id={}", id);

            return ResponseEntity.ok(ApiResponse.success("Promo deleted", null));

        } catch (RuntimeException e) {
            log.warn("[ADMIN][PROMO] Promo not found for deletion → id={} reason={}", id, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Promo not found: " + id));

        } catch (IOException e) {
            log.error("[ADMIN][PROMO] Cloudinary deletion failed → id={} reason={}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to delete promo image: " + e.getMessage()));

        } catch (Exception e) {
            log.error("[ADMIN][PROMO] Unexpected error deleting promo → id={} reason={}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete promo"));
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard Stats
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/stats
     * High-level platform statistics for the admin dashboard.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboardStats() {

        log.info("[ADMIN][STATS] Fetching dashboard stats");

        try {
            DashboardStatsResponse stats = adminDashboardService.getDashboardStats();

            log.info("[ADMIN][STATS] Stats fetched → totalUsers={} activeVip={}",
                    stats.getTotalUsers(), stats.getActiveVipUsers());

            return ResponseEntity.ok(ApiResponse.success("Dashboard stats", stats));

        } catch (Exception e) {
            log.error("[ADMIN][STATS] Failed to fetch dashboard stats → reason={}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch dashboard stats"));
        }
    }
}