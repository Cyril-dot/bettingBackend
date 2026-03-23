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

    @Query("SELECT v FROM VipSubscription v WHERE v.active = true " +
           "AND v.expiresAt < :now")
    List<VipSubscription> findExpiredSubscriptions(LocalDateTime now);

    @Deprecated
    Optional<VipSubscription> findByUserAndActiveTrue(User user);

    /**
     * @deprecated Use {@link #countActiveAndNotExpired(LocalDateTime)} instead.
     *             This query does not validate expiresAt and inflates VIP counts.
     */
    @Deprecated
    long countByActiveTrue();


    // ── CORRECT — expiry-aware queries (use these everywhere) ────────────────

    /**
     * Returns the user's subscription only if it is both active=true AND
     * expiresAt is still in the future. This is the single source of truth
     * for whether a user currently has a valid VIP subscription.
     *
     * Replaces: findByUserAndActiveTrue(user)
     */
    @Query("SELECT v FROM VipSubscription v " +
            "WHERE v.user = :user " +
            "AND v.active = true " +
            "AND v.expiresAt > :now")
    Optional<VipSubscription> findActiveAndNotExpired(
            @Param("user") User user,
            @Param("now")  LocalDateTime now);

    /**
     * Returns all subscriptions where active=true but expiresAt is in the past.
     * Used by the scheduler to batch-expire stale records.
     */
    @Query("SELECT v FROM VipSubscription v " +
            "WHERE v.active = true " +
            "AND v.expiresAt <= :now")
    List<VipSubscription> findExpiredButStillActive(@Param("now") LocalDateTime now);

    /**
     * Counts only subscriptions that are both active=true AND not yet expired.
     * Use this for dashboard stats instead of countByActiveTrue().
     */
    @Query("SELECT COUNT(v) FROM VipSubscription v " +
            "WHERE v.active = true " +
            "AND v.expiresAt > :now")
    long countActiveAndNotExpired(@Param("now") LocalDateTime now);

    /**
     * Used by the webhook idempotency check in PaystackService.handleWebhook().
     * This remains unchanged — it checks payment records, not subscriptions.
     */
    boolean existsByUserAndActiveTrue(User user);
}