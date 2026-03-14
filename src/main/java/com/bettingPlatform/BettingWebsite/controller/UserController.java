package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.UserPrincipal;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.service.UserRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Public auth endpoints  → /api/v1/auth  (permit-listed in SecurityConfig)
 * Authenticated user ops → /api/v1/users (requires USER role)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserRegistrationService userService;


    // ─────────────────────────────────────────────────────────────
    // PUBLIC — no auth required (permit in SecurityConfig)
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/auth/register
     * Open to everyone — must be permit-listed in SecurityConfig.
     */
    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody CreateUserRequest request) {

        log.info("[AUTH] Register attempt → email={}", request.getEmail());

        try {
            UserResponse response = userService.createUser(request);
            log.info("[AUTH] User registered successfully → userId={} email={}",
                    response.getId(), response.getEmail());
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("User registered successfully", response));

        } catch (IllegalArgumentException e) {
            log.warn("[AUTH] Registration rejected → email={} reason={}",
                    request.getEmail(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[AUTH] Registration failed → email={} reason={}",
                    request.getEmail(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Registration failed. Please try again."));
        }
    }

    /**
     * POST /api/v1/auth/login
     * Open to everyone — must be permit-listed in SecurityConfig.
     */
    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        log.info("[AUTH] Login attempt → email={}", request.getEmail());

        try {
            LoginResponse response = userService.userLogin(request);
            log.info("[AUTH] Login successful → userId={} email={}",
                    response.getUserId(), response.getEmail());
            return ResponseEntity.ok(
                    ApiResponse.success("Login successful", response));

        } catch (IllegalArgumentException e) {
            log.warn("[AUTH] Login rejected → email={} reason={}",
                    request.getEmail(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid credentials"));

        } catch (Exception e) {
            log.error("[AUTH] Login failed → email={} reason={}",
                    request.getEmail(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Login failed. Please try again."));
        }
    }


    // ─────────────────────────────────────────────────────────────
    // AUTHENTICATED — USER role required
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/users/me
     * Returns the authenticated user's profile.
     */
    @GetMapping("/api/v1/users/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserDetails(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("[USER] Profile requested → userId={}", principal.getUserId());

        try {
            UserResponse response = userService.getUserDetails(principal.getUserId());
            log.info("[USER] Profile returned → userId={} email={}",
                    principal.getUserId(), response.getEmail());
            return ResponseEntity.ok(
                    ApiResponse.success("User fetched successfully", response));

        } catch (RuntimeException e) {
            log.warn("[USER] User not found → userId={} reason={}",
                    principal.getUserId(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found"));

        } catch (Exception e) {
            log.error("[USER] Failed to fetch profile → userId={} reason={}",
                    principal.getUserId(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch user details"));
        }
    }

    /**
     * PATCH /api/v1/users/me
     * Updates display name, preferences, or other non-sensitive fields.
     */
    @PatchMapping("/api/v1/users/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateUserRequest request) {

        log.info("[USER] Profile update requested → userId={}", principal.getUserId());

        try {
            UserResponse response = userService.updateUser(principal.getUserId(), request);
            log.info("[USER] Profile updated successfully → userId={}", principal.getUserId());
            return ResponseEntity.ok(
                    ApiResponse.success("User updated successfully", response));

        } catch (IllegalArgumentException e) {
            log.warn("[USER] Profile update rejected → userId={} reason={}",
                    principal.getUserId(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[USER] Profile update failed → userId={} reason={}",
                    principal.getUserId(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update user"));
        }
    }



}