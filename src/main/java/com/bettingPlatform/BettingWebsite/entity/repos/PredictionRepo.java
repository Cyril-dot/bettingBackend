package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PredictionRepo extends JpaRepository<Prediction, UUID> {

    List<Prediction> findByMatchDateAndPublishedTrue(LocalDate date);

    List<Prediction> findByTypeAndMatchDateAndPublishedTrue(
            PredictionType type, LocalDate date);

    List<Prediction> findByStatusAndMatchDateLessThanEqual(
            PredictionStatus status, LocalDate date);

    List<Prediction> findByMatchDateBeforeAndStatusAndPublishedTrue(
            LocalDate date, PredictionStatus status);

    long countByStatus(PredictionStatus status);

    // All pending predictions that have an external fixture ID
    @Query("SELECT p FROM Prediction p WHERE p.status = 'PENDING' " +
           "AND p.externalFixtureId IS NOT NULL " +
           "AND p.matchDate <= :today")
    List<Prediction> findPendingWithFixtureId(LocalDate today);

    // All predictions for a specific game
    List<Prediction> findByGame(Game game);
}