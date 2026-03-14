package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BettingCodeRepo extends JpaRepository<BettingCode, UUID> {
    List<BettingCode> findByValidDateAndPublishedTrue(LocalDate date);
    List<BettingCode> findByTypeAndValidDateAndPublishedTrue(PredictionType type, LocalDate date);
}