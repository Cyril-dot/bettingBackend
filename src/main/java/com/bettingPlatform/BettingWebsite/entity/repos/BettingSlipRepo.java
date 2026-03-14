package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.BettingSlip;
import com.bettingPlatform.BettingWebsite.entity.PredictionType;
import com.bettingPlatform.BettingWebsite.entity.SlipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BettingSlipRepo extends JpaRepository<BettingSlip, UUID> {

    List<BettingSlip> findByPublishedTrue();
    List<BettingSlip> findByTypeAndPublishedTrue(PredictionType type);
    List<BettingSlip> findByStatusAndPublishedTrue(SlipStatus status);
    List<BettingSlip> findByBookmakerAndPublishedTrue(String bookmaker);
    List<BettingSlip> findByStatusAndValidUntilBefore(SlipStatus status, LocalDateTime now);

    // ── new ──
    List<BettingSlip> findAllByOrderByCreatedAtDesc();
}