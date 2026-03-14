package com.bettingPlatform.BettingWebsite.controller;

import com.bettingPlatform.BettingWebsite.Config.Security.AdminPrincipal;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    // ─────────────────────────────────────────────────────────────
    // Auth
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/admin/create
     *
     * ⚠️  Consider restricting this to an existing ADMIN or a one-time
     *     setup token rather than leaving it fully open.
     */
    @PostMapping("/create")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> createAdmin(
            @Valid @RequestBody CreateAdminRequest request) {

        log.info("[ADMIN][AUTH] Admin creation attempt → email={}", request.getEmail());

        try {
            AdminLoginResponse response = adminService.createAdmin(request);

            log.info("[ADMIN][AUTH] Admin created successfully → email={}", request.getEmail());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Admin created successfully", response));

        } catch (RuntimeException e) {
            log.warn("[ADMIN][AUTH] Admin creation rejected → email={} reason={}",
                    request.getEmail(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[ADMIN][AUTH] Unexpected error during admin creation → email={} reason={}",
                    request.getEmail(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Admin creation failed"));
        }
    }

    /**
     * POST /api/v1/admin/login
     */
    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(
            @Valid @RequestBody AdminLoginRequest request) {

        log.info("[ADMIN][AUTH] Login attempt → email={}", request.getEmail());

        try {
            AdminLoginResponse response = adminService.adminLogin(request);

            log.info("[ADMIN][AUTH] Login successful → email={}", request.getEmail());

            return ResponseEntity.ok(ApiResponse.success("Admin login successful", response));

        } catch (RuntimeException e) {
            log.warn("[ADMIN][AUTH] Login failed → email={} reason={}", request.getEmail(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid credentials"));

        } catch (Exception e) {
            log.error("[ADMIN][AUTH] Unexpected login error → email={} reason={}",
                    request.getEmail(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Login failed"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Admin Profile
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AdminResponse>> getAdminDetails(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {

        log.info("[ADMIN][PROFILE] Fetching own details → adminId={} email={}",
                adminPrincipal.getSellerId(), adminPrincipal.getEmail());

        try {
            AdminResponse response = adminService.getAdminDetails(adminPrincipal);

            log.info("[ADMIN][PROFILE] Details fetched → adminId={}", adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Admin details fetched", response));

        } catch (RuntimeException e) {
            log.warn("[ADMIN][PROFILE] Admin not found → adminId={} reason={}",
                    adminPrincipal.getSellerId(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Admin not found"));

        } catch (Exception e) {
            log.error("[ADMIN][PROFILE] Failed to fetch admin details → adminId={} reason={}",
                    adminPrincipal.getSellerId(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch admin details"));
        }
    }

    /**
     * PATCH /api/v1/admin/me
     */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<AdminResponse>> updateAdmin(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @Valid @RequestBody UpdateAdminRequest request) {

        log.info("[ADMIN][PROFILE] Update request → adminId={} email={}",
                adminPrincipal.getSellerId(), adminPrincipal.getEmail());

        try {
            AdminResponse response = adminService.updateAdmin(adminPrincipal, request);

            log.info("[ADMIN][PROFILE] Updated successfully → adminId={}", adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Admin updated successfully", response));

        } catch (RuntimeException e) {
            log.warn("[ADMIN][PROFILE] Update rejected → adminId={} reason={}",
                    adminPrincipal.getSellerId(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("[ADMIN][PROFILE] Unexpected error updating admin → adminId={} reason={}",
                    adminPrincipal.getSellerId(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update admin"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // User Management
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/users
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getAllUsers(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal) {

        log.info("[ADMIN][USERS] Fetching all users → requestedBy={}", adminPrincipal.getSellerId());

        try {
            List<UserSummaryResponse> users = adminService.getAllUsers();

            log.info("[ADMIN][USERS] Users fetched → count={} requestedBy={}",
                    users.size(), adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("Users fetched successfully", users));

        } catch (Exception e) {
            log.error("[ADMIN][USERS] Failed to fetch users → requestedBy={} reason={}",
                    adminPrincipal.getSellerId(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch users"));
        }
    }

    /**
     * GET /api/v1/admin/users/{userId}
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getUserDetails(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable UUID userId) {

        log.info("[ADMIN][USERS] Fetching user details → userId={} requestedBy={}",
                userId, adminPrincipal.getSellerId());

        try {
            UserSummaryResponse user = adminService.getUserDetails(userId);

            log.info("[ADMIN][USERS] User details fetched → userId={} requestedBy={}",
                    userId, adminPrincipal.getSellerId());

            return ResponseEntity.ok(ApiResponse.success("User details fetched", user));

        } catch (RuntimeException e) {
            log.warn("[ADMIN][USERS] User not found → userId={} requestedBy={} reason={}",
                    userId, adminPrincipal.getSellerId(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found: " + userId));

        } catch (Exception e) {
            log.error("[ADMIN][USERS] Failed to fetch user details → userId={} requestedBy={} reason={}",
                    userId, adminPrincipal.getSellerId(), e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch user details"));
        }
    }
}