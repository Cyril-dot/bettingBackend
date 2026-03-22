package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.dto.AdminPaymentStatsResponse;
import com.bettingPlatform.BettingWebsite.service.AdminPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")   // protect all endpoints — ADMIN only
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    /**
     * GET /api/admin/payments/stats
     *
     * Returns:
     *  - dailyTotalUsd   + dailyCount
     *  - weeklyTotalUsd  + weeklyCount
     *  - totalUsd        + totalCount
     *  - activeVips
     *  - currencyBreakdown  { GHS: x, NGN: y, USD: z }
     *  - last7Days          [ { date, totalUsd, count } × 7 ]
     *  - recentPayments     [ last 10 successful payments ]
     */
    @GetMapping("/stats")
    public ResponseEntity<AdminPaymentStatsResponse> getStats() {
        return ResponseEntity.ok(adminPaymentService.getSummaryStats());
    }

    /**
     * GET /api/admin/payments/all
     *
     * Full payment list (all statuses) sorted newest-first.
     * Used to populate the admin data table.
     */
    @GetMapping("/all")
    public ResponseEntity<List<AdminPaymentStatsResponse.RecentPayment>> getAllPayments() {
        return ResponseEntity.ok(adminPaymentService.getAllPayments());
    }
}