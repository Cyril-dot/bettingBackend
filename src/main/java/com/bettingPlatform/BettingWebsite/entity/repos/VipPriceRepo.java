package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.VipPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface VipPriceRepo extends JpaRepository<VipPrice, UUID> {
    Optional<VipPrice> findByActiveTrue();
}