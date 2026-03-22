package com.bettingPlatform.BettingWebsite.service;

import com.bettingPlatform.BettingWebsite.dto.AdminPaymentStatsResponse;
import com.bettingPlatform.BettingWebsite.entity.Payment;
import com.bettingPlatform.BettingWebsite.entity.PaymentStatus;
import com.bettingPlatform.BettingWebsite.entity.repos.PaymentRepo;
import com.bettingPlatform.BettingWebsite.entity.repos.VipSubscriptionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPaymentService {

    private final PaymentRepo         paymentRepo;
    private final VipSubscriptionRepo vipSubscriptionRepo;
    private final CurrencyConverter   currencyConverter;

    // ════════════════════════════════════════════════════════════════
    // SUMMARY STATS — daily, weekly, total (all converted to USD)
    // ════════════════════════════════════════════════════════════════

    public AdminPaymentStatsResponse getSummaryStats() {

        LocalDateTime now       = LocalDateTime.now();
        LocalDateTime startOfDay  = now.with(LocalTime.MIDNIGHT);
        LocalDateTime startOfWeek = now.minusDays(6).with(LocalTime.MIDNIGHT);

        List<Payment> allSuccess = paymentRepo.findByStatus(PaymentStatus.SUCCESS);

        // ── Daily ────────────────────────────────────────────────────
        List<Payment> todayPayments = allSuccess.stream()
                .filter(p -> p.getPaidAt() != null && p.getPaidAt().isAfter(startOfDay))
                .collect(Collectors.toList());

        double dailyTotalUsd = sumToUsd(todayPayments);
        int    dailyCount    = todayPayments.size();

        // ── Weekly (last 7 days) ──────────────────────────────────────
        List<Payment> weekPayments = allSuccess.stream()
                .filter(p -> p.getPaidAt() != null && p.getPaidAt().isAfter(startOfWeek))
                .collect(Collectors.toList());

        double weeklyTotalUsd = sumToUsd(weekPayments);
        int    weeklyCount    = weekPayments.size();

        // ── All-time total ────────────────────────────────────────────
        double totalUsd   = sumToUsd(allSuccess);
        int    totalCount = allSuccess.size();

        // ── Active VIPs right now ────────────────────────────────────
        long activeVips = vipSubscriptionRepo.countByActiveTrue();

        // ── Currency breakdown (all-time) ────────────────────────────
        Map<String, Double> currencyBreakdown = allSuccess.stream()
                .collect(Collectors.groupingBy(
                        Payment::getCurrency,
                        Collectors.summingDouble(Payment::getAmount)
                ));

        // ── Daily revenue for last 7 days (sparkline data) ───────────
        List<AdminPaymentStatsResponse.DailyRevenue> last7Days = buildLast7Days(allSuccess);

        // ── Recent payments (last 10) ────────────────────────────────
        List<AdminPaymentStatsResponse.RecentPayment> recent = allSuccess.stream()
                .sorted(Comparator.comparing(Payment::getPaidAt).reversed())
                .limit(10)
                .map(this::toRecentPayment)
                .collect(Collectors.toList());

        log.info("📊 Admin stats — Today: {} txns (${} USD) | Week: {} txns (${} USD) | Total: {} txns (${} USD)",
                dailyCount, String.format("%.2f", dailyTotalUsd),
                weeklyCount, String.format("%.2f", weeklyTotalUsd),
                totalCount, String.format("%.2f", totalUsd));

        return AdminPaymentStatsResponse.builder()
                .dailyTotalUsd(dailyTotalUsd)
                .dailyCount(dailyCount)
                .weeklyTotalUsd(weeklyTotalUsd)
                .weeklyCount(weeklyCount)
                .totalUsd(totalUsd)
                .totalCount(totalCount)
                .activeVips(activeVips)
                .currencyBreakdown(currencyBreakdown)
                .last7Days(last7Days)
                .recentPayments(recent)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // ALL PAYMENTS (pageable table for admin UI)
    // ════════════════════════════════════════════════════════════════

    public List<AdminPaymentStatsResponse.RecentPayment> getAllPayments() {
        return paymentRepo.findAll().stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt).reversed())
                .map(this::toRecentPayment)
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════

    /**
     * Converts all payments in the list to USD and sums them.
     */
    private double sumToUsd(List<Payment> payments) {
        return payments.stream()
                .mapToDouble(p -> currencyConverter.convert(p.getAmount(), p.getCurrency(), "USD"))
                .sum();
    }

    /**
     * Builds revenue per day for the last 7 days (including today).
     * Days with no payments get 0.00.
     */
    private List<AdminPaymentStatsResponse.DailyRevenue> buildLast7Days(List<Payment> allSuccess) {
        List<AdminPaymentStatsResponse.DailyRevenue> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate day        = today.minusDays(i);
            LocalDateTime start  = day.atStartOfDay();
            LocalDateTime end    = day.atTime(LocalTime.MAX);

            List<Payment> dayPayments = allSuccess.stream()
                    .filter(p -> p.getPaidAt() != null
                            && !p.getPaidAt().isBefore(start)
                            && !p.getPaidAt().isAfter(end))
                    .collect(Collectors.toList());

            double dayUsd = sumToUsd(dayPayments);

            result.add(AdminPaymentStatsResponse.DailyRevenue.builder()
                    .date(day)
                    .totalUsd(dayUsd)
                    .count(dayPayments.size())
                    .build());
        }
        return result;
    }

    private AdminPaymentStatsResponse.RecentPayment toRecentPayment(Payment p) {
        double usdEquivalent = currencyConverter.convert(p.getAmount(), p.getCurrency(), "USD");
        return AdminPaymentStatsResponse.RecentPayment.builder()
                .id(p.getId())
                .reference(p.getReference())
                .userEmail(p.getEmail())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .usdEquivalent(usdEquivalent)
                .status(p.getStatus())
                .channel(p.getChannel())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}