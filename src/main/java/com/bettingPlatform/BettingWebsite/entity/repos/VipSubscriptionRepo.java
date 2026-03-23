package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.VipSubscription;
import com.bettingPlatform.BettingWebsite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VipSubscriptionRepo extends JpaRepository<VipSubscription, UUID> {

    /**
     * Primary VIP status check — used everywhere in PaystackService and
     * AdminDashboardService in place of the old findByUserAndActiveTrue().
     *
     * Validates BOTH conditions: active=true AND expiresAt > now.
     * This means:
     *   - Admin price changes have zero effect on active subscribers
     *   - A subscription whose 24h window has passed is never treated as valid
     *     even if the scheduler hasn't flipped its active flag yet
     */
    @Query("SELECT s FROM VipSubscription s WHERE s.user = :user AND s.active = true AND s.expiresAt > :now")
    Optional<VipSubscription> findActiveAndNotExpired(@Param("user") User user,
                                                      @Param("now") LocalDateTime now);

    /**
     * Real-time VIP user count for the admin dashboard.
     * Replaces countByActiveTrue() which inflated the count by including
     * subscriptions whose expiresAt had already passed but whose active
     * flag hadn't been reset by the scheduler yet.
     */
    @Query("SELECT COUNT(s) FROM VipSubscription s WHERE s.active = true AND s.expiresAt > :now")
    long countActiveAndNotExpired(@Param("now") LocalDateTime now);

    /**
     * Used by VipExpiryScheduler (runs every 10 minutes).
     * Finds subscriptions where active=true but expiresAt is in the past
     * so the scheduler can flip them to active=false.
     */
    @Query("SELECT s FROM VipSubscription s WHERE s.active = true AND s.expiresAt <= :now")
    List<VipSubscription> findExpiredButStillActive(@Param("now") LocalDateTime now);

    /**
     * Used by PaystackService.handleWebhook() to guard against double-activation
     * if existsByReferenceAndStatus is not available on PaymentRepo.
     * Keep this if anything in your codebase still calls it directly.
     */
    boolean existsByUserAndActiveTrue(User user);

    Optional<VipSubscription> findByUser(User user);
}