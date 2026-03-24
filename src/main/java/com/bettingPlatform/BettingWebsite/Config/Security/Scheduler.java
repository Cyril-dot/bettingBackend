package com.bettingPlatform.BettingWebsite.Config.Security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class Scheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(Scheduler.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final JdbcTemplate jdbcTemplate;

    private static final String RENDER_URL = "https://bettingbackend-y3ck.onrender.com";

    public Scheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Ping every 5 minutes (300,000 ms)
    @Scheduled(fixedRate = 30000) // 30 seconds
    public void keepAlive() {
        pingDatabase();
    }

    private void pingRender() {
        try {
            String response = restTemplate.getForObject(RENDER_URL, String.class);
            logger.info("Render keep-alive ping successful at {} | Response: {}",
                    java.time.LocalDateTime.now(), response);
        } catch (Exception e) {
            logger.error("Render keep-alive ping failed at {} | Error: {}",
                    java.time.LocalDateTime.now(), e.getMessage());
        }
    }

    private void pingDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            logger.info("Railway DB keep-alive ping successful at {}",
                    java.time.LocalDateTime.now());
        } catch (Exception e) {
            logger.error("Railway DB keep-alive ping failed at {} | Error: {}",
                    java.time.LocalDateTime.now(), e.getMessage());
        }
    }
}