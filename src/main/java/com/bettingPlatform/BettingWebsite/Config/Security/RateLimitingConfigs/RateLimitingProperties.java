package com.bettingPlatform.BettingWebsite.Config.Security.RateLimitingConfigs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rate.limit")
public class RateLimitingProperties {

    private int capacity = 100;
    private long refillSeconds = 60;
    private boolean enabled = true;
    private String[] excludedPaths = {
            "/actuator/**",
            "/error",
            "/favicon.ico",
            "/.well-known/**",
            "/api/v1/users/register",
            "/api/v1/users/login",
            "/api/v1/admin/login",
            "/ws/**"              // exclude WebSocket from rate limiting
    };
    private boolean trackByIp = true;
    private long maxCacheSize = 100000;

    // Different limits per endpoint type
    private int publicLimit = 100;       // public endpoints
    private int authenticatedLimit = 200; // logged in users
    private int adminLimit = 500;         // admin endpoints
    private int vipLimit = 300;           // VIP users
}