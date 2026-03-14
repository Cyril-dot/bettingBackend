package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.VipSubscription;
import com.bettingPlatform.BettingWebsite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VipSubscriptionRepo extends JpaRepository<VipSubscription, UUID> {

    Optional<VipSubscription> findByUserAndActiveTrue(User user);

    @Query("SELECT v FROM VipSubscription v WHERE v.active = true " +
           "AND v.expiresAt < :now")
    List<VipSubscription> findExpiredSubscriptions(LocalDateTime now);

    long countByActiveTrue();
}