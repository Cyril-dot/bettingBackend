package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.Game;
import com.bettingPlatform.BettingWebsite.entity.GameStatus;
import io.micrometer.common.KeyValues;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameRepo extends JpaRepository<Game, UUID> {

    Optional<Game> findByExternalFixtureId(String externalFixtureId);
    Optional<Game> findByHomeTeamAndAwayTeam(String homeTeam, String awayTeam);
    List<Game> findByStatus(GameStatus status);
    List<Game> findByPublishedTrue();
    List<Game> findByFeaturedTrue();
    List<Game> findByStatusAndPublishedTrue(GameStatus status);
    List<Game> findByStatusOrderByKickoffTimeAsc(GameStatus status);
    List<Game> findByVipOnlyTrueAndPublishedTrue();

    @Query("SELECT g FROM Game g WHERE g.published = true " +
            "AND g.kickoffTime >= :startOfDay " +
            "AND g.kickoffTime <= :endOfDay " +
            "ORDER BY g.kickoffTime ASC")
    List<Game> findTodayPublishedGames(@Param("startOfDay") LocalDateTime startOfDay,
                                       @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT g FROM Game g WHERE g.kickoffTime >= :startOfDay " +
            "AND g.kickoffTime <= :endOfDay " +
            "ORDER BY g.kickoffTime ASC")
    List<Game> findAllTodayGames(@Param("startOfDay") LocalDateTime startOfDay,
                                 @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT g FROM Game g WHERE g.status = 'FINISHED' " +
            "AND g.updatedAt >= :since")
    List<Game> findRecentlyFinishedGames(@Param("since") LocalDateTime since);

    @Query("SELECT g FROM Game g WHERE g.published = true " +
            "AND g.vipOnly = true " +
            "AND g.kickoffTime >= :startOfDay " +
            "AND g.kickoffTime <= :endOfDay " +
            "ORDER BY g.kickoffTime ASC")
    List<Game> findVipOnlyTodayGames(@Param("startOfDay") LocalDateTime startOfDay,
                                     @Param("endOfDay") LocalDateTime endOfDay);

    // Count by status — for admin dashboard
    long countByStatus(GameStatus status);

    // Count published games
    long countByPublishedTrue();

    // Count VIP only games
    long countByVipOnlyTrue();

    // Find by league
    List<Game> findByLeagueAndPublishedTrue(String league);

    // Find VIP live games
    List<Game> findByStatusAndVipOnlyTrueAndPublishedTrue(GameStatus status);

    // Find featured + published + not VIP
    List<Game> findByFeaturedTrueAndPublishedTrueAndVipOnlyFalse();

    // Find featured VIP games
    List<Game> findByFeaturedTrueAndPublishedTrueAndVipOnlyTrue();

    // Find upcoming games
    @Query("SELECT g FROM Game g WHERE g.published = true " +
            "AND g.status = 'UPCOMING' " +
            "AND g.vipOnly = false " +
            "ORDER BY g.kickoffTime ASC")
    List<Game> findUpcomingPublicGames();

    // Find upcoming VIP games
    @Query("SELECT g FROM Game g WHERE g.published = true " +
            "AND g.status = 'UPCOMING' " +
            "AND g.vipOnly = true " +
            "ORDER BY g.kickoffTime ASC")
    List<Game> findUpcomingVipGames();

    // Find games by league and date range
    @Query("SELECT g FROM Game g WHERE g.league = :league " +
            "AND g.published = true " +
            "AND g.kickoffTime >= :startOfDay " +
            "AND g.kickoffTime <= :endOfDay " +
            "ORDER BY g.kickoffTime ASC")
    List<Game> findByLeagueAndDate(@Param("league") String league,
                                   @Param("startOfDay") LocalDateTime startOfDay,
                                   @Param("endOfDay") LocalDateTime endOfDay);

    // Find games starting soon (within next X minutes)
    @Query("SELECT g FROM Game g WHERE g.published = true " +
            "AND g.kickoffTime >= :now " +
            "AND g.kickoffTime <= :soon " +
            "ORDER BY g.kickoffTime ASC")
    List<Game> findGamesStartingSoon(@Param("now") LocalDateTime now,
                                     @Param("soon") LocalDateTime soon);

    @Query("SELECT g FROM Game g WHERE " +
            "LOWER(REPLACE(REPLACE(REPLACE(REPLACE(g.homeTeam, ' FC', ''), ' AFC', ''), ' CF', ''), ' SC', '')) " +
            "LIKE LOWER(CONCAT('%', :homeTeam, '%')) AND " +
            "LOWER(REPLACE(REPLACE(REPLACE(REPLACE(g.awayTeam, ' FC', ''), ' AFC', ''), ' CF', ''), ' SC', '')) " +
            "LIKE LOWER(CONCAT('%', :awayTeam, '%'))")
    Optional<Game> findByHomeTeamContainingAndAwayTeamContaining(
            @Param("homeTeam") String homeTeam,
            @Param("awayTeam") String awayTeam);


    @Query("""
    SELECT g FROM Game g
    WHERE g.status IN ('FINISHED', 'CANCELLED', 'POSTPONED')
      AND g.published = true
      AND g.kickoffTime < :before
    ORDER BY g.kickoffTime DESC
""")
    List<Game> findPreviousPublishedGames(
            @Param("before") LocalDateTime before,
            Pageable pageable
    );

    // For VIP — same but includes vipOnly games
    @Query("""
    SELECT g FROM Game g
    WHERE g.status IN ('FINISHED', 'CANCELLED', 'POSTPONED')
      AND g.published = true
      AND g.kickoffTime < :before
    ORDER BY g.kickoffTime DESC
""")
    List<Game> findAllPreviousGames(
            @Param("before") LocalDateTime before,
            Pageable pageable
    );

    // Count helpers
    long countByStatusIn(List<GameStatus> statuses);


    @Query("""
    SELECT g FROM Game g
    WHERE g.kickoffTime <= :cutoff
    AND g.kickoffTime >= :startOfDay
    AND g.status NOT IN ('FINISHED', 'CANCELLED', 'POSTPONED')
    ORDER BY g.kickoffTime ASC
    """)
    List<Game> findStaleGames(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("startOfDay") LocalDateTime startOfDay
    );


    @Query("SELECT g FROM Game g WHERE g.kickoffTime >= :from AND g.kickoffTime <= :to AND g.published = true ORDER BY g.kickoffTime ASC")
    List<Game> findPublishedGamesBetween(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to
    );

}