package com.bettingPlatform.BettingWebsite.Config.Security.RateLimitingConfigs;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
public class RateLimitingService {

    private final Cache<String, Bucket> cache;
    private final RateLimitingProperties properties;

    public RateLimitingService(RateLimitingProperties properties) {
        this.properties = properties;

        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getMaxCacheSize())
                .expireAfterAccess(Duration.ofSeconds(properties.getRefillSeconds()))
                .recordStats()
                .build();

        log.info("✅ Rate limiting initialized — Capacity: {} requests per {}s",
                properties.getCapacity(), properties.getRefillSeconds());
    }

    // Create bucket based on user type
    public Bucket resolveBucket(String key, RateLimitTier tier) {
        String tieredKey = tier.name() + ":" + key;
        return cache.get(tieredKey, k -> createBucketForTier(tier));
    }

    // Fallback for non-tiered calls
    public Bucket resolveBucket(String key) {
        return cache.get(key, k -> createBucketForTier(RateLimitTier.PUBLIC));
    }

    private Bucket createBucketForTier(RateLimitTier tier) {
        int capacity = switch (tier) {
            case PUBLIC -> properties.getPublicLimit();
            case AUTHENTICATED -> properties.getAuthenticatedLimit();
            case VIP -> properties.getVipLimit();
            case ADMIN -> properties.getAdminLimit();
        };

        Refill refill = Refill.greedy(capacity, Duration.ofSeconds(properties.getRefillSeconds()));
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        return Bucket.builder().addLimit(limit).build();
    }

    public boolean tryConsume(String key) {
        return resolveBucket(key).tryConsume(1);
    }

    public boolean tryConsume(String key, RateLimitTier tier) {
        return resolveBucket(key, tier).tryConsume(1);
    }

    public long getAvaliableTokens(String key) {
        return resolveBucket(key).getAvailableTokens();
    }

    public long getSecondsUntilRefil(String key) {
        Bucket bucket = resolveBucket(key);
        return bucket.getAvailableTokens() == 0
                ? properties.getRefillSeconds() - (System.currentTimeMillis() / 1000 % properties.getRefillSeconds())
                : 0;
    }

    public Map<String, Object> getCacheStats() {
        var stats = cache.stats();
        return Map.of(
                "hitRate", stats.hitRate(),
                "missRate", stats.missRate(),
                "evictionCount", stats.evictionCount(),
                "estimatedSize", cache.estimatedSize(),
                "timestamp", LocalDateTime.now().toString()
        );
    }

    public void clearCache() {
        cache.invalidateAll();
        log.info("🧹 Rate limit cache cleared");
    }

    public void removeKey(String key) {
        cache.invalidate(key);
        log.info("🗑️ Key {} removed from cache", key);
    }
}