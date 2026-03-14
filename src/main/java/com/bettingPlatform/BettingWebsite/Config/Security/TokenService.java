package com.bettingPlatform.BettingWebsite.Config.Security;

import com.bettingPlatform.BettingWebsite.Config.Security.entity.AdminRefreshToken;
import com.bettingPlatform.BettingWebsite.Config.Security.entity.AdminRefreshTokenRepo;
import com.bettingPlatform.BettingWebsite.Config.Security.entity.RefreshToken;
import com.bettingPlatform.BettingWebsite.Config.Security.entity.RefreshTokenRepo;
import com.bettingPlatform.BettingWebsite.entity.Admin;
import com.bettingPlatform.BettingWebsite.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-expiration-ms}")
    private long accessExpirationMs;

    @Value("${jwt.access.admin-expiration-ms}")
    private long adminAccessTokenExpirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private final RefreshTokenRepo refreshTokenRepo;
    private final AdminRefreshTokenRepo adminRepo;
    private final TokenEncryptionService encryptionService;

    /**
     * Ensure HS256 key is 256 bits (32 bytes)
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new RuntimeException("JWT secret key must be at least 32 bytes for HS256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ===================== ACCESS TOKEN =====================

    /**
     * Generate encrypted access token
     * Returns: Encrypted JWT (unreadable without decryption key)
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationMs);

        log.info("🔐 Generating access token for user: {}", user.getEmail());

        // Step 1: Create plain JWT with user claims
        String plainToken = Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();

        log.debug("Plain JWT created (length: {})", plainToken.length());

        // Step 2: Encrypt the JWT
        String encryptedToken = encryptionService.encryptToken(plainToken);

        log.info("🔒 Generated encrypted access token for user: {} (encrypted length: {})",
                user.getEmail(), encryptedToken.length());
        log.debug("Encrypted token preview: {}...", encryptedToken.substring(0, Math.min(50, encryptedToken.length())));

        return encryptedToken;
    }

    // to generate access token for owner
    public String generateOwnerAccessToken(Admin seller) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + adminAccessTokenExpirationMs);

        log.info("🔐 Generating access token for owner: {}", seller.getEmail());

        // Step 1: Create plain JWT with seller claims
        String plainToken = Jwts.builder()
                .subject(seller.getEmail())
                .claim("userId", seller.getId())
                .claim("role", seller.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();

        log.debug("Plain JWT created (length: {})", plainToken.length());

        // Step 2: Encrypt the JWT
        String encryptedToken = encryptionService.encryptToken(plainToken);

        log.info("🔒 Generated encrypted access token for owner: {} (encrypted length: {})",
                seller.getEmail(), encryptedToken.length());
        log.debug("Encrypted token preview: {}...", encryptedToken.substring(0, Math.min(50, encryptedToken.length())));

        return encryptedToken;
    }

    // ===================== REFRESH TOKEN =====================

    /**
     * Generate encrypted refresh token and store in database
     */
    public RefreshToken generateRefreshToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);

        log.info("🔐 Generating refresh token for user: {}", user.getEmail());

        // Step 1: Create plain JWT for refresh token
        String plainRefreshToken = Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("tokenType", "REFRESH")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();

        // Step 2: Encrypt the refresh token
        String encryptedRefreshToken = encryptionService.encryptToken(plainRefreshToken);

        log.debug("Encrypted refresh token created (length: {})", encryptedRefreshToken.length());

        // Step 3: Store encrypted token in database
        Instant expiryInstant = Instant.now().plusMillis(refreshExpirationMs);
        refreshTokenRepo.upsertUserRefreshToken(user.getId(), encryptedRefreshToken, expiryInstant);

        log.info("🔒 Generated encrypted refresh token for user: {}", user.getEmail());

        return refreshTokenRepo.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Failed to create refresh token for user"));
    }

    // now refresh token for owner
    public AdminRefreshToken generateAdminRefreshToken(Admin seller) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);

        log.info("🔐 Generating refresh token for owner: {}", seller.getEmail());

        // Step 1: Create plain JWT for refresh token
        String plainRefreshToken = Jwts.builder()
                .subject(seller.getEmail())
                .claim("userId", seller.getId())
                .claim("tokenType", "REFRESH")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();

        // Step 2: Encrypt the refresh token
        String encryptedRefreshToken = encryptionService.encryptToken(plainRefreshToken);

        log.debug("Encrypted refresh token created (length: {})", encryptedRefreshToken.length());

        // Step 3: Store encrypted token in database
        Instant expiryInstant = Instant.now().plusMillis(refreshExpirationMs);
        adminRepo.upsertAdminRefreshToken(seller.getId(), encryptedRefreshToken, expiryInstant);

        log.info("🔒 Generated encrypted refresh token for owner: {}", seller.getEmail());

        return adminRepo.findByAdmin(seller)
                .orElseThrow(() -> new RuntimeException("Failed to create refresh token for seller"));
    }

    // ===================== VALIDATION =====================

    /**
     * Validate encrypted access token
     * Step 1: Decrypt token
     * Step 2: Validate JWT signature and expiration
     */
    public boolean validateAccessToken(String encryptedToken) {
        try {
            log.debug("🔍 Validating access token (encrypted length: {})", encryptedToken.length());
            log.debug("Encrypted token preview: {}...", encryptedToken.substring(0, Math.min(50, encryptedToken.length())));

            // Step 1: Decrypt the token
            String plainToken = encryptionService.decryptToken(encryptedToken);
            log.debug("✅ Token decrypted successfully (plain length: {})", plainToken.length());

            // Step 2: Validate JWT
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(plainToken)
                    .getPayload();

            log.debug("✅ Token validated successfully for user: {}", claims.getSubject());
            log.debug("Token expires at: {}", claims.getExpiration());
            log.debug("Token issued at: {}", claims.getIssuedAt());

            return true;

        } catch (ExpiredJwtException ex) {
            log.warn("⚠️ Token expired for user: {}", ex.getClaims().getSubject());
            throw new RuntimeException("Token expired", ex);
        } catch (JwtException ex) {
            log.error("❌ Invalid JWT token: {}", ex.getMessage());
            throw new RuntimeException("Invalid token", ex);
        } catch (Exception ex) {
            log.error("❌ Token decryption failed: {}", ex.getMessage(), ex);
            throw new RuntimeException("Invalid or corrupted token", ex);
        }
    }

    /**
     * Validate encrypted refresh token
     */
    public boolean validateRefreshToken(String encryptedRefreshToken) {
        try {
            log.debug("🔍 Validating refresh token");

            // Step 1: Check if token exists in database
            RefreshToken refreshToken = refreshTokenRepo.findByToken(encryptedRefreshToken)
                    .orElseThrow(() -> new RuntimeException("Refresh token not found"));

            log.debug("Refresh token found in database for user: {}", refreshToken.getUser().getEmail());

            // Step 2: Check if token is expired
            if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
                log.warn("⚠️ Refresh token expired for user: {}", refreshToken.getUser().getEmail());
                refreshTokenRepo.delete(refreshToken);
                throw new RuntimeException("Refresh token expired");
            }

            // Step 3: Decrypt and validate JWT
            String plainToken = encryptionService.decryptToken(encryptedRefreshToken);
            log.debug("✅ Refresh token decrypted successfully");

            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(plainToken);

            log.debug("✅ Refresh token validated successfully for user: {}", refreshToken.getUser().getEmail());
            return true;

        } catch (ExpiredJwtException ex) {
            log.warn("⚠️ Refresh token expired");
            throw new RuntimeException("Refresh token expired", ex);
        } catch (JwtException ex) {
            log.error("❌ Invalid refresh token: {}", ex.getMessage());
            throw new RuntimeException("Invalid refresh token", ex);
        } catch (Exception ex) {
            log.error("❌ Refresh token validation failed: {}", ex.getMessage());
            throw new RuntimeException("Invalid or corrupted refresh token", ex);
        }
    }

    // to validate admin refresh token
    public boolean validateAdminRefreshToken(String encryptedRefreshToken) {
        try {
            log.debug("🔍 Validating admin refresh token");

            // Step 1: Check if token exists in database
            AdminRefreshToken refreshToken = adminRepo.findByToken(encryptedRefreshToken)
                    .orElseThrow(() -> new RuntimeException("Refresh token not found"));

            log.debug("Refresh token found in database for owner: {}", refreshToken.getAdmin().getEmail());

            // Step 2: Check if token is expired
            if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
                log.warn("⚠️ Refresh token expired for owner: {}", refreshToken.getAdmin().getEmail());
                adminRepo.delete(refreshToken);
                throw new RuntimeException("Refresh token expired");
            }

            // Step 3: Decrypt and validate JWT
            String plainToken = encryptionService.decryptToken(encryptedRefreshToken);
            log.debug("✅ Refresh token decrypted successfully");

            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(plainToken);

            log.debug("✅ Refresh token validated successfully for owner: {}", refreshToken.getAdmin().getEmail());
            return true;

        } catch (ExpiredJwtException ex) {
            log.warn("⚠️ Refresh token expired");
            throw new RuntimeException("Refresh token expired", ex);
        } catch (JwtException ex) {
            log.error("❌ Invalid refresh token: {}", ex.getMessage());
            throw new RuntimeException("Invalid refresh token", ex);
        } catch (Exception ex) {
            log.error("❌ Refresh token validation failed: {}", ex.getMessage());
            throw new RuntimeException("Invalid or corrupted refresh token", ex);
        }
    }

    // ===================== EXTRACT CLAIMS (WITH DECRYPTION) =====================

    /**
     * Extract email from encrypted access token
     */
    public String getEmailFromAccessToken(String encryptedToken) {
        try {
            log.debug("🔍 Extracting email from encrypted token (length: {})", encryptedToken.length());

            // Step 1: Decrypt the token
            String plainToken = encryptionService.decryptToken(encryptedToken);
            log.debug("✅ Token decrypted for email extraction");

            // Step 2: Extract email from JWT
            String email = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(plainToken)
                    .getPayload()
                    .getSubject();  // Email is the subject

            log.debug("✅ Email extracted: {}", email);
            return email;

        } catch (ExpiredJwtException ex) {
            log.error("❌ Token expired while extracting email: {}", ex.getClaims().getSubject());
            throw new RuntimeException("Token expired", ex);
        } catch (JwtException ex) {
            log.error("❌ Invalid token while extracting email: {}", ex.getMessage());
            throw new RuntimeException("Invalid token", ex);
        } catch (Exception ex) {
            log.error("❌ Failed to decrypt token for email extraction: {}", ex.getMessage(), ex);
            throw new RuntimeException("Invalid or corrupted token", ex);
        }
    }

    /**
     * Extract user ID from encrypted access token
     */
    public Long getUserIdFromAccessToken(String encryptedToken) {
        try {
            log.debug("🔍 Extracting userId from encrypted token");

            // Step 1: Decrypt the token
            String plainToken = encryptionService.decryptToken(encryptedToken);
            log.debug("✅ Token decrypted for userId extraction");

            // Step 2: Extract userId from claims
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(plainToken)
                    .getPayload();

            Long userId = claims.get("userId", Long.class);
            log.debug("✅ UserId extracted: {}", userId);

            return userId;

        } catch (ExpiredJwtException ex) {
            log.error("❌ Token expired while extracting userId");
            throw new RuntimeException("Token expired", ex);
        } catch (JwtException ex) {
            log.error("❌ Invalid token while extracting userId: {}", ex.getMessage());
            throw new RuntimeException("Invalid token", ex);
        } catch (Exception ex) {
            log.error("❌ Failed to decrypt token for userId extraction: {}", ex.getMessage(), ex);
            throw new RuntimeException("Invalid or corrupted token", ex);
        }
    }

    /**
     * Extract role from encrypted access token
     */
    public String getRoleFromAccessToken(String encryptedToken) {
        try {
            log.debug("🔍 Extracting role from encrypted token");

            // Step 1: Decrypt the token
            String plainToken = encryptionService.decryptToken(encryptedToken);
            log.debug("✅ Token decrypted for role extraction");

            // Step 2: Extract role from claims
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(plainToken)
                    .getPayload();

            String role = claims.get("role", String.class);
            log.debug("✅ Role extracted: {}", role);

            return role;

        } catch (ExpiredJwtException ex) {
            log.error("❌ Token expired while extracting role");
            throw new RuntimeException("Token expired", ex);
        } catch (JwtException ex) {
            log.error("❌ Invalid token while extracting role: {}", ex.getMessage());
            throw new RuntimeException("Invalid token", ex);
        } catch (Exception ex) {
            log.error("❌ Failed to decrypt token for role extraction: {}", ex.getMessage(), ex);
            throw new RuntimeException("Invalid or corrupted token", ex);
        }
    }

    // ===================== TOKEN REFRESH =====================

    /**
     * Refresh access token using encrypted refresh token
     * Returns new encrypted access token
     */
    public String refreshAccessToken(String encryptedRefreshToken) {
        try {
            log.info("🔄 Refreshing access token");

            // Step 1: Validate refresh token
            validateRefreshToken(encryptedRefreshToken);

            // Step 2: Get user from refresh token
            RefreshToken refreshToken = refreshTokenRepo.findByToken(encryptedRefreshToken)
                    .orElseThrow(() -> new RuntimeException("Refresh token not found"));

            User user = refreshToken.getUser();
            log.debug("Refresh token belongs to user: {}", user.getEmail());

            // Step 3: Generate new encrypted access token
            String newAccessToken = generateAccessToken(user);

            log.info("🔄 Access token refreshed for user: {}", user.getEmail());
            return newAccessToken;

        } catch (Exception ex) {
            log.error("❌ Failed to refresh access token: {}", ex.getMessage());
            throw new RuntimeException("Failed to refresh token", ex);
        }
    }

    // ===================== TOKEN REVOCATION =====================

    /**
     * Revoke (delete) refresh token
     */
    public void revokeRefreshToken(String encryptedRefreshToken) {
        try {
            log.info("🗑️ Revoking refresh token");

            refreshTokenRepo.findByToken(encryptedRefreshToken)
                    .ifPresent(token -> {
                        refreshTokenRepo.delete(token);
                        log.info("🗑️ Refresh token revoked for user: {}", token.getUser().getEmail());
                    });
        } catch (Exception ex) {
            log.error("❌ Failed to revoke refresh token: {}", ex.getMessage());
            throw new RuntimeException("Failed to revoke token", ex);
        }
    }

    /**
     * Revoke all refresh tokens for a user (e.g., on password change)
     */
    public void revokeAllUserRefreshTokens(User user) {
        try {
            log.info("🗑️ Revoking all refresh tokens for user: {}", user.getEmail());

            refreshTokenRepo.findByUser(user).ifPresent(token -> {
                refreshTokenRepo.delete(token);
                log.info("🗑️ All refresh tokens revoked for user: {}", user.getEmail());
            });
        } catch (Exception ex) {
            log.error("❌ Failed to revoke user refresh tokens: {}", ex.getMessage());
            throw new RuntimeException("Failed to revoke tokens", ex);
        }
    }

    // ===================== HELPER METHODS =====================

    /**
     * Check if encrypted token is expired without throwing exception
     */
    public boolean isTokenExpired(String encryptedToken) {
        try {
            log.debug("🔍 Checking if token is expired");

            String plainToken = encryptionService.decryptToken(encryptedToken);

            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(plainToken)
                    .getPayload();

            boolean isExpired = claims.getExpiration().before(new Date());
            log.debug("Token expired: {}", isExpired);

            return isExpired;

        } catch (ExpiredJwtException ex) {
            log.debug("Token is expired");
            return true;
        } catch (Exception ex) {
            log.error("❌ Failed to check token expiration: {}", ex.getMessage());
            return true;
        }
    }

    /**
     * Get remaining time until token expiration (in milliseconds)
     */
    public long getTokenExpirationTime(String encryptedToken) {
        try {
            log.debug("🔍 Getting token expiration time");

            String plainToken = encryptionService.decryptToken(encryptedToken);

            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(plainToken)
                    .getPayload();

            Date expiration = claims.getExpiration();
            long remainingTime = expiration.getTime() - System.currentTimeMillis();

            log.debug("Token expires in {} milliseconds", remainingTime);

            return Math.max(0, remainingTime);

        } catch (Exception ex) {
            log.error("❌ Failed to get token expiration time: {}", ex.getMessage());
            return 0;
        }
    }
}