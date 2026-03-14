package com.bettingPlatform.BettingWebsite.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        // "*" pattern works with allowCredentials(true) in setAllowedOriginPatterns
        // This covers all origins including mobile on LAN, file://, ngrok, etc.
        config.setAllowedOriginPatterns(List.of("*"));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Allow all headers — avoids mobile browsers stripping or sending unexpected headers
        config.setAllowedHeaders(List.of("*"));

        // true is required when using setAllowedOriginPatterns("*")
        // Fine for Bearer token auth (JWT in Authorization header, not cookies)
        config.setAllowCredentials(true);

        config.setMaxAge(3600L);

        config.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}