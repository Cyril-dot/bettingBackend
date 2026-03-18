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
     * A promo is "active" if now is between startsAt and expiresAt.
     * No stored `active` column needed — computed from dates only.
     */
    @Query("SELECT p FROM Promo p WHERE p.startsAt <= :now AND p.expiresAt > :now")
    List<Promo> findActivePromos(@Param("now") LocalDateTime now);
}