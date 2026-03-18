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

    @PostMapping("/vip/price")
    public ResponseEntity<ApiResponse<VipPriceResponse>> setVipPrice(
            @Valid @RequestBody SetVipPriceRequest request) {

        log.info("[ADMIN][VIP] Setting VIP price → amount={} currency={}",
                request.getPrice(), request.getCurrency());
        try {
            VipPriceResponse response = adminDashboardService.setVipPrice(request);
            log.info("[ADMIN][VIP] Price updated → id={} amount={} {}",
                    response.getId(), response.getPrice(), response.getCurrency());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("VIP price updated", response));
        } catch (RuntimeException e) {
            log.warn("[ADMIN][VIP] Invalid request → reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[ADMIN][VIP] Failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update VIP price: " + e.getMessage()));
        }
    }

    @GetMapping("/vip/price")
    public ResponseEntity<ApiResponse<VipPriceResponse>> getCurrentVipPrice() {
        log.info("[ADMIN][VIP] Fetching current VIP price");
        try {
            VipPriceResponse response = adminDashboardService.getCurrentVipPrice();
            return ResponseEntity.ok(ApiResponse.success("Current VIP price", response));
        } catch (RuntimeException e) {
            log.warn("[ADMIN][VIP] No active price → reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No active VIP price configured"));
        } catch (Exception e) {
            log.error("[ADMIN][VIP] Failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch VIP price"));
        }
    }

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
     * Create a new promotion with optional image upload.
     */
    @PostMapping(value = "/promos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PromoResponse>> createPromo(
            @Valid @ModelAttribute CreatePromoRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        log.info("[ADMIN][PROMO] Creating promo → title='{}' type={}", request.getTitle(), request.getType());
        try {
            PromoResponse response = adminDashboardService.createPromo(request, image);
            log.info("[ADMIN][PROMO] Created → id={} title='{}'", response.getId(), response.getTitle());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Promo created", response));
        } catch (IOException e) {
            log.error("[ADMIN][PROMO] Image upload failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Image upload failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("[ADMIN][PROMO] Unexpected error → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create promo: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/admin/promos
     * Returns ALL promos — upcoming, active, and expired.
     * Admin needs to see everything, not just what's currently live.
     */
    @GetMapping("/promos")
    public ResponseEntity<ApiResponse<List<PromoResponse>>> getAllPromos() {
        log.info("[ADMIN][PROMO] Fetching ALL promos");
        try {
            List<PromoResponse> promos = adminDashboardService.getAllPromos();
            log.info("[ADMIN][PROMO] Fetched {} promos", promos.size());
            return ResponseEntity.ok(ApiResponse.success("All promos", promos));
        } catch (Exception e) {
            log.error("[ADMIN][PROMO] Failed to fetch promos → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch promos"));
        }
    }

    /**
     * DELETE /api/v1/admin/promos/{id}
     * Delete a promotion and its Cloudinary image.
     */
    @DeleteMapping("/promos/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePromo(@PathVariable UUID id) {
        log.info("[ADMIN][PROMO] Deleting promo → id={}", id);
        try {
            adminDashboardService.deletePromo(id);
            log.info("[ADMIN][PROMO] Deleted → id={}", id);
            return ResponseEntity.ok(ApiResponse.success("Promo deleted", null));
        } catch (RuntimeException e) {
            log.warn("[ADMIN][PROMO] Not found → id={} reason={}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Promo not found: " + id));
        } catch (IOException e) {
            log.error("[ADMIN][PROMO] Cloudinary deletion failed → id={} reason={}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to delete promo image: " + e.getMessage()));
        } catch (Exception e) {
            log.error("[ADMIN][PROMO] Unexpected error → id={} reason={}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete promo"));
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard Stats
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboardStats() {
        log.info("[ADMIN][STATS] Fetching dashboard stats");
        try {
            DashboardStatsResponse stats = adminDashboardService.getDashboardStats();
            log.info("[ADMIN][STATS] Fetched → totalUsers={} activeVip={}",
                    stats.getTotalUsers(), stats.getActiveVipUsers());
            return ResponseEntity.ok(ApiResponse.success("Dashboard stats", stats));
        } catch (Exception e) {
            log.error("[ADMIN][STATS] Failed → reason={}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch dashboard stats"));
        }
    }
}