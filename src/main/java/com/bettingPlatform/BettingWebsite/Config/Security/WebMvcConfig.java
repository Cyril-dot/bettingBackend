package com.bettingPlatform.BettingWebsite.Config.Security;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, LocalDateTime.class, source -> {
            if (source == null || source.isBlank()) return null;

            // Normalize: replace space with T for ISO compliance
            String normalized = source.trim().replace(" ", "T");

            // Try full ISO: 2026-03-12T14:00:00
            try {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignored) {}

            // Try without seconds: 2026-03-12T14:00
            try {
                return LocalDateTime.parse(normalized,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            } catch (DateTimeParseException ignored) {}

            // Try date only: 2026-03-12
            try {
                return LocalDateTime.parse(normalized + "T00:00:00",
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Cannot parse date-time: '" + source + "'. " +
                        "Expected format: yyyy-MM-ddTHH:mm or yyyy-MM-dd HH:mm");
            }
        });
    }
}