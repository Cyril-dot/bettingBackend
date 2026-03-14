package com.bettingPlatform.BettingWebsite.Config.Security;

import com.bettingPlatform.BettingWebsite.entity.repos.AdminRepo;
import com.bettingPlatform.BettingWebsite.entity.repos.UserRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepo userRepo;
    private final AdminRepo adminRepo;

    private static final List<String> PUBLIC_AUTH_ENDPOINTS = Arrays.asList(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/v1/auth",
            "/api/v1/admin/login",   // ← add this so login doesn't need a token
            "/api/v1/admin/create"   // ← add this so registration doesn't need a token
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (PUBLIC_AUTH_ENDPOINTS.stream().anyMatch(path::startsWith)) return true;
        return path.startsWith("/login")
                || path.startsWith("/api/test/")
                || path.startsWith("/actuator/")
                || path.startsWith("/ws/")
                || path.startsWith("/ws-meeting/")
                || path.equals("/favicon.ico")
                || path.startsWith("/.well-known/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        var existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null
                && existingAuth.isAuthenticated()
                && !(existingAuth instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = header.substring(7);
            String email = tokenService.getEmailFromAccessToken(token);
            String role  = tokenService.getRoleFromAccessToken(token);

            log.debug("🔍 Token role claim: '{}' for email: {}", role, email);

            UserDetails userDetails;

            // ✅ FIXED: was "SELLER" — must match whatever Admin.getRole().name() returns
            if ("ADMIN".equals(role) || "SELLER".equals(role)) {
                var adminOptional = adminRepo.findByEmail(email);
                if (adminOptional.isEmpty()) {
                    log.warn("❌ No admin found for email: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }
                userDetails = new AdminPrincipal(adminOptional.get());

            } else {
                var userOptional = userRepo.findByEmail(email);
                if (userOptional.isEmpty()) {
                    log.warn("❌ No user found for email: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }
                userDetails = new UserPrincipal(userOptional.get());
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.info("✅ Authentication successful for: {}", email);

        } catch (Exception e) {
            log.error("💥 JWT authentication error: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}