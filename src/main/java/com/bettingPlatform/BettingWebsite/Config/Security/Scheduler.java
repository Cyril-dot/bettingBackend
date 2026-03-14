package com.bettingPlatform.BettingWebsite.Config.Security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class Scheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(Scheduler.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String RENDER_URL = "https://billionbackend.onrender.com/ping";

    // Ping every 5 minutes (300,000 ms)
    @Scheduled(fixedRate = 5000)
    public void keepAlive() {
        try {
            String response = restTemplate.getForObject(RENDER_URL, String.class);
            logger.info("Keep-alive ping successful at {} | Response: {}",
                    java.time.LocalDateTime.now(), response);
        } catch (Exception e) {
            logger.error("Keep-alive ping failed at {} | Error: {}",
                    java.time.LocalDateTime.now(), e.getMessage());
        }
    }
}