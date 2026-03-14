package com.bettingPlatform.BettingWebsite.Config.Security.entity;

import com.bettingPlatform.BettingWebsite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepo extends JpaRepository<RefreshToken, Long> {

    /**
     * Find refresh token by the token string itself
     * ✅ CRITICAL: This was missing and is needed for token validation
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Find refresh token by User entity
     */
    Optional<RefreshToken> findByUser(User user);

    /**
     * Find refresh token by user ID
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId")
    Optional<RefreshToken> findByUserId(@Param("userId") UUID userId);

    /**
     * Upsert refresh token for a user (PostgreSQL syntax)
     * Inserts new token or updates existing one for the user
     */
    @Modifying
    @Transactional
    @Query(
            value = """
            INSERT INTO refresh_tokens (user_id, token, expiry_date)
            VALUES (:userId, :token, :expiry)
            ON CONFLICT (user_id)
            DO UPDATE SET
                token = EXCLUDED.token,
                expiry_date = EXCLUDED.expiry_date
        """,
            nativeQuery = true
    )
    void upsertUserRefreshToken(
            @Param("userId") UUID userId,
            @Param("token") String token,
            @Param("expiry") Instant expiry
    );

    /**
     * Delete expired refresh tokens (for scheduled cleanup)
     * Run this periodically to clean up the database
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    int deleteExpiredTokens(@Param("now") Instant now);

    /**
     * Delete all refresh tokens for a specific user
     * Useful for logout, password change, or security revocation
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    /**
     * Check if a refresh token exists and is not expired
     */
    @Query("""
        SELECT CASE WHEN COUNT(rt) > 0 THEN true ELSE false END
        FROM RefreshToken rt
        WHERE rt.token = :token
        AND rt.expiryDate > :now
    """)
    boolean existsByTokenAndNotExpired(
            @Param("token") String token,
            @Param("now") Instant now
    );

    /**
     * Count active (non-expired) refresh tokens for a user
     */
    @Query("""
        SELECT COUNT(rt)
        FROM RefreshToken rt
        WHERE rt.user.id = :userId
        AND rt.expiryDate > :now
    """)
    long countActiveTokensByUserId(
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );
}