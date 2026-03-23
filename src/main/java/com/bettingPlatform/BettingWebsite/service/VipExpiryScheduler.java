package com.bettingPlatform.BettingWebsite.service;

import com.bettingPlatform.BettingWebsite.entity.VipSubscription;
import com.bettingPlatform.BettingWebsite.entity.repos.VipSubscriptionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler that keeps VipSubscription.active in sync with VipSubscription.expiresAt.
 *
 * Without this, the active flag stays true forever — even after the 24-hour window
 * has passed — because nothing resets it. That causes two problems:
 *
 *   1. findByUserAndActiveTrue() returns expired subscriptions as if they're valid.
 *   2. countByActiveTrue() inflates the active VIP count on the admin dashboard.
 *
 * This job runs every 10 minutes and sets active=false on any subscription whose
 * expiresAt is in the past. The expiry-aware repo queries (findActiveAndNotExpired,
 * countActiveAndNotExpired) are the primary source of truth, but keeping the active
 * flag accurate ensures any raw queries or future repo additions stay correct too.
 *
 * IMPORTANT: Add @EnableScheduling to your main @SpringBootApplication class.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VipExpiryScheduler {

    private final VipSubscriptionRepo vipSubscriptionRepo;

    /**
     * Runs every 10 minutes (600,000 ms).
     * Finds all subscriptions where active=true but expiresAt is in the past,
     * and flips them to active=false.
     */
    @Scheduled(fixedRate = 600_000)
    public void expireStaleSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<VipSubscription> expired = vipSubscriptionRepo.findExpiredButStillActive(now);

        if (expired.isEmpty()) {
            log.debug("⏰ VIP expiry check — no stale subscriptions found");
            return;
        }

        expired.forEach(sub -> {
            sub.setActive(false);
            log.info("⏰ VIP expired — userId={} email={} expiredAt={}",
                    sub.getUser().getId(),
                    sub.getUser().getEmail(),
                    sub.getExpiresAt());
        });

        vipSubscriptionRepo.saveAll(expired);
        log.info("⏰ VIP expiry job complete — {} subscription(s) deactivated", expired.size());
    }
}