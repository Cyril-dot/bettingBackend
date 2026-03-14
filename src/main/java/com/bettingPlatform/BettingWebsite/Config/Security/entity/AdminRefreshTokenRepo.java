package com.bettingPlatform.BettingWebsite.Config.Security.entity;

import com.bettingPlatform.BettingWebsite.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AdminRefreshTokenRepo extends JpaRepository<AdminRefreshToken, Long> {
    /**
     * Find refresh token by the token string itself
     * ✅ CRITICAL: This was missing and is needed for token validation
     */
    Optional<AdminRefreshToken> findByToken(String token);

    /**
     * Find refresh token by User entity
     */
    Optional<AdminRefreshToken> findByAdmin(Admin admin);

    /**
     * Upsert refresh token for a user (PostgreSQL syntax)
     * Inserts new token or updates existing one for the user
     */
    @Modifying
    @Transactional
    @Query(
            value = """
            INSERT INTO admin_tokens (admin_id, token, expiry_date)
            VALUES (:adminId, :token, :expiry)
            ON CONFLICT (admin_id)
            DO UPDATE SET
                token = EXCLUDED.token,
                expiry_date = EXCLUDED.expiry_date
        """,
            nativeQuery = true
    )
    void upsertAdminRefreshToken(@Param("adminId") UUID adminId,
                                 @Param("token") String token,
                                 @Param("expiry") Instant expiryDate);  // ← "expiry" not "expiryDate"
}
