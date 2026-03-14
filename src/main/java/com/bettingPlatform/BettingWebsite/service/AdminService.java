package com.bettingPlatform.BettingWebsite.service;

import com.bettingPlatform.BettingWebsite.Config.Security.AdminPrincipal;
import com.bettingPlatform.BettingWebsite.Config.Security.TokenService;
import com.bettingPlatform.BettingWebsite.Config.Security.UserPrincipal;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.entity.Admin;
import com.bettingPlatform.BettingWebsite.entity.LastLogin;
import com.bettingPlatform.BettingWebsite.entity.User;
import com.bettingPlatform.BettingWebsite.entity.repos.AdminRepo;
import com.bettingPlatform.BettingWebsite.entity.repos.LastLoginRepo;
import com.bettingPlatform.BettingWebsite.entity.repos.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final AdminRepo adminRepo;
    private final UserRepo userRepo;
    private final LastLoginRepo lastLoginRepo;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AdminLoginResponse createAdmin(CreateAdminRequest request) {
        if (adminRepo.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        Admin admin = Admin.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .createdAt(LocalDateTime.now())
                .build();

        Admin savedAdmin = adminRepo.save(admin);
        log.info("New admin created: {}", savedAdmin.getEmail());

        String accessToken = tokenService.generateOwnerAccessToken(savedAdmin);
        String refreshToken = tokenService.generateAdminRefreshToken(savedAdmin).getToken();

        return AdminLoginResponse.builder()
                .adminId(savedAdmin.getId())
                .fullName(savedAdmin.getFullName())
                .email(savedAdmin.getEmail())
                .role(savedAdmin.getRole())
                .token(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public AdminLoginResponse adminLogin(AdminLoginRequest request) {
        Admin admin = adminRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        log.info("Admin logged in: {}", admin.getEmail());

        String accessToken = tokenService.generateOwnerAccessToken(admin);
        String refreshToken = tokenService.generateAdminRefreshToken(admin).getToken();

        return AdminLoginResponse.builder()
                .adminId(admin.getId())
                .fullName(admin.getFullName())
                .email(admin.getEmail())
                .role(admin.getRole())
                .token(accessToken)
                .refreshToken(refreshToken)
                .build();
    }


    public AdminResponse getAdminDetails(AdminPrincipal userPrincipal) {
        Admin admin = adminRepo.findById(userPrincipal.getSellerId())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        return AdminResponse.builder()
                .id(admin.getId())
                .fullName(admin.getFullName())
                .email(admin.getEmail())
                .role(admin.getRole())
                .createdAt(admin.getCreatedAt())
                .updatedAt(admin.getUpdatedAt())
                .build();
    }


    public AdminResponse updateAdmin(AdminPrincipal userPrincipal, UpdateAdminRequest request) {
        Admin admin = adminRepo.findById(userPrincipal.getSellerId())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            admin.setFullName(request.getFullName());
        }

        if (request.getEmail() != null && !request.getEmail().equals(admin.getEmail())) {
            if (adminRepo.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Email already in use");
            }
            admin.setEmail(request.getEmail());
        }

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null ||
                    !passwordEncoder.matches(request.getCurrentPassword(), admin.getPassword())) {
                throw new RuntimeException("Current password is incorrect");
            }
            if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
                throw new RuntimeException("New passwords do not match");
            }
            admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        admin.setUpdatedAt(LocalDateTime.now());
        Admin updatedAdmin = adminRepo.save(admin);
        log.info("Admin updated: {}", updatedAdmin.getEmail());

        return AdminResponse.builder()
                .id(updatedAdmin.getId())
                .fullName(updatedAdmin.getFullName())
                .email(updatedAdmin.getEmail())
                .role(updatedAdmin.getRole())
                .createdAt(updatedAdmin.getCreatedAt())
                .updatedAt(updatedAdmin.getUpdatedAt())
                .build();
    }


    public List<UserSummaryResponse> getAllUsers() {
        List<User> users = userRepo.findAll();

        return users.stream().map(user -> {
            LocalDateTime lastLogin = lastLoginRepo.findByUser(user)
                    .map(LastLogin::getLastLoginAt)
                    .orElse(null);

            return UserSummaryResponse.builder()
                    .id(user.getId())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .phoneNumber(user.getPhoneNumber())
                    .role(user.getRole())
                    .createdAt(user.getCreatedAt())
                    .lastLogin(lastLogin)
                    .build();
        }).collect(Collectors.toList());
    }


    public UserSummaryResponse getUserDetails(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime lastLogin = lastLoginRepo.findByUser(user)
                .map(LastLogin::getLastLoginAt)
                .orElse(null);

        return UserSummaryResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .lastLogin(lastLogin)
                .build();
    }
}