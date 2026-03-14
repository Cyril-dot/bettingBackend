package com.bettingPlatform.BettingWebsite.Config.Security.RateLimitingConfigs;

import com.bettingPlatform.BettingWebsite.Config.Security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final RateLimitingProperties rateLimitingProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!rateLimitingProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestPath = request.getRequestURI();
        if (isExcludedPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String identifier = getIdentifier(request);
        RateLimitTier tier = resolveTier(request);

        boolean allowed = rateLimitingService.tryConsume(identifier, tier);

        if (allowed) {
            long remaining = rateLimitingService.getAvaliableTokens(identifier);
            response.setHeader("X-RateLimit-Limit",
                    String.valueOf(getTierCapacity(tier)));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset",
                    String.valueOf(System.currentTimeMillis() / 1000
                            + rateLimitingProperties.getRefillSeconds()));
            response.setHeader("X-RateLimit-Tier", tier.name());

            log.debug("✅ [{}] {} — Remaining: {}", tier, identifier, remaining);
            filterChain.doFilter(request, response);

        } else {
            long retryAfter = rateLimitingService.getSecondsUntilRefil(identifier);

            log.warn("🚫 Rate limit exceeded — [{}] {} — Path: {} — Retry: {}s",
                    tier, identifier, requestPath, retryAfter);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-RateLimit-Limit", String.valueOf(getTierCapacity(tier)));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset",
                    String.valueOf(System.currentTimeMillis() / 1000 + retryAfter));
            response.setHeader("Retry-After", String.valueOf(retryAfter));

            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
            errorBody.put("error", "Too Many Requests");
            errorBody.put("message", String.format(
                    "Rate limit exceeded. Max %d requests per %ds. Retry in %ds.",
                    getTierCapacity(tier),
                    rateLimitingProperties.getRefillSeconds(),
                    retryAfter));
            errorBody.put("retryAfter", retryAfter);
            errorBody.put("path", requestPath);
            errorBody.put("tier", tier.name());
            errorBody.put("timestamp", LocalDateTime.now().toString());

            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            response.getWriter().flush();
        }
    }

    private RateLimitTier resolveTier(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return RateLimitTier.PUBLIC;
        }

        // Check roles
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return RateLimitTier.ADMIN;

        boolean isVip = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_VIP"));
        if (isVip) return RateLimitTier.VIP;

        return RateLimitTier.AUTHENTICATED;
    }

    private int getTierCapacity(RateLimitTier tier) {
        return switch (tier) {
            case PUBLIC -> rateLimitingProperties.getPublicLimit();
            case AUTHENTICATED -> rateLimitingProperties.getAuthenticatedLimit();
            case VIP -> rateLimitingProperties.getVipLimit();
            case ADMIN -> rateLimitingProperties.getAdminLimit();
        };
    }

    private String getIdentifier(HttpServletRequest request) {
        if (rateLimitingProperties.isTrackByIp()) {
            return getClientIP(request);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return getClientIP(request);
    }

    private String getClientIP(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR",
                "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR", "REMOTE_ADDR"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.contains(",") ? ip.split(",")[0].trim() : ip;
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isExcludedPath(String path) {
        return Arrays.stream(rateLimitingProperties.getExcludedPaths())
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}