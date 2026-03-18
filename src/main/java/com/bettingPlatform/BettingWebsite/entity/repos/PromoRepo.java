package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.Promo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PromoRepo extends JpaRepository<Promo, UUID> {

    /**
     * Used by the PUBLIC endpoint — only returns promos currently in their active window.
     * A promo is "live" if now is between startsAt and expiresAt.
     */
    @Query("SELECT p FROM Promo p WHERE p.startsAt <= :now AND p.expiresAt > :now ORDER BY p.startsAt DESC")
    List<Promo> findActivePromos(@Param("now") LocalDateTime now);

    /**
     * Used by the ADMIN endpoint — returns ALL promos regardless of date,
     * ordered by creation date (newest first).
     * Admins need to see upcoming, live, and expired promos.
     */
    @Query("SELECT p FROM Promo p ORDER BY p.createdAt DESC")
    List<Promo> findAllPromos();
}