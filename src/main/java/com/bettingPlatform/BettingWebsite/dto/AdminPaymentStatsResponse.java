package com.bettingPlatform.BettingWebsite.dto;

import com.bettingPlatform.BettingWebsite.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class AdminPaymentStatsResponse {

    // ── Summary numbers ──────────────────────────────────────────────
    private double        dailyTotalUsd;    // revenue today (in USD)
    private int           dailyCount;       // transactions today

    private double        weeklyTotalUsd;   // revenue last 7 days (in USD)
    private int           weeklyCount;      // transactions last 7 days

    private double        totalUsd;         // all-time revenue (in USD)
    private int           totalCount;       // all-time transactions

    private long          activeVips;       // currently active VIP subs

    // ── Breakdown ────────────────────────────────────────────────────
    private Map<String, Double> currencyBreakdown;  // { "GHS": 1450.00, "NGN": 33000.00 }

    // ── Chart data (last 7 days) ──────────────────────────────────────
    private List<DailyRevenue> last7Days;

    // ── Recent transactions ───────────────────────────────────────────
    private List<RecentPayment> recentPayments;

    private LocalDateTime generatedAt;

    // ── Nested: per-day revenue ───────────────────────────────────────
    @Data
    @Builder
    public static class DailyRevenue {
        private LocalDate date;
        private double    totalUsd;
        private int       count;
    }

    // ── Nested: single payment row ────────────────────────────────────
    @Data
    @Builder
    public static class RecentPayment {
        private UUID id;
        private String        reference;
        private String        userEmail;
        private double        amount;
        private String        currency;
        private double        usdEquivalent;
        private PaymentStatus status;
        private String        channel;
        private LocalDateTime paidAt;
        private LocalDateTime createdAt;
    }
}