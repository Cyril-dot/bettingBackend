package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.dto.ApiResponse;
import com.bettingPlatform.BettingWebsite.dto.PromoResponse;
import com.bettingPlatform.BettingWebsite.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public Promo Controller
 *
 * No authentication required.
 * Permitted via SecurityConfig: /api/v1/public/**
 *
 * Base mapping is /api/v1/public (class level) + /promos (method level)
 * → final URL: GET /api/v1/public/promos
 *
 * Splitting the path across class + method level is required for Spring
 * to correctly register the route — putting the full path only on the
 * class with no method-level mapping can cause 404s.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Slf4j
public class PublicPromoController {

    private final AdminDashboardService adminDashboardService;

    /**
     * GET /api/v1/public/promos
     *
     * Returns promos that are currently active (now is between startsAt and expiresAt).
     * Safe to call without a JWT — no sensitive data exposed.
     *
     * Response example:
     * {
     *   "success": true,
     *   "message": "Active promotions",
     *   "data": [
     *     {
     *       "id": "...",
     *       "title": "Weekend Special",
     *       "description": "50% off VIP this weekend",
     *       "imageUrl": "https://res.cloudinary.com/...",
     *       "type": "DISCOUNT",
     *       "discountPercent": 50.0,
     *       "startsAt": "2026-03-18T00:00:00",
     *       "expiresAt": "2026-03-20T23:59:59",
     *       "active": true
     *     }
     *   ]
     * }
     */
    @GetMapping("/promos")
    public ResponseEntity<ApiResponse<List<PromoResponse>>> getActivePromos() {
        log.info("[PUBLIC][PROMO] Fetching active promos");
        try {
            List<PromoResponse> promos = adminDashboardService.getActivePromos();
            log.info("[PUBLIC][PROMO] Returning {} active promos", promos.size());
            return ResponseEntity.ok(ApiResponse.success("Active promotions", promos));
        } catch (Exception e) {
            log.error("[PUBLIC][PROMO] Failed → reason={}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch promotions"));
        }
    }
}