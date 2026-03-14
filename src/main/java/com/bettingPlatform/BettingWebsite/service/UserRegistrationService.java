package com.bettingPlatform.BettingWebsite.service;

import com.bettingPlatform.BettingWebsite.Config.Security.TokenService;
import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.entity.LastLogin;
import com.bettingPlatform.BettingWebsite.entity.User;
import com.bettingPlatform.BettingWebsite.entity.repos.LastLoginRepo;
import com.bettingPlatform.BettingWebsite.entity.repos.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepo userRepo;
    private final LastLoginRepo lastLoginRepo;
    private final TokenService tokenService;


    public UserResponse createUser(CreateUserRequest request) {
        if (userRepo.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }
        if (userRepo.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new RuntimeException("Phone number already exists");
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());

        if (request.getPassword().equals(request.getConfirmPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        } else {
            throw new RuntimeException("Passwords do not match");
        }

        user.setPhoneNumber(request.getPhoneNumber());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepo.save(user);
        log.info("New user registered: {}", savedUser.getEmail());

        String accessToken = tokenService.generateAccessToken(savedUser);
        String refreshToken = tokenService.generateRefreshToken(savedUser).getToken();

        return UserResponse.builder()
                .id(savedUser.getId())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .phoneNumber(savedUser.getPhoneNumber())
                .createdAt(savedUser.getCreatedAt())
                .role(savedUser.getRole())
                .token(accessToken)
                .refreshToken(refreshToken)
                .build();
    }


    public LoginResponse userLogin(LoginRequest request) {
        User user = userRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        // After validating password, before returning response — save last login
        LastLogin lastLogin = lastLoginRepo.findByUser(user)
                .orElse(LastLogin.builder().user(user).build());

        lastLogin.setLastLoginAt(LocalDateTime.now());
        lastLoginRepo.save(lastLogin);

        log.info("User logged in: {}", user.getEmail());

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user).getToken();

        return LoginResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .token(accessToken)
                .refreshToken(refreshToken)
                .build();
    }


    public UserResponse getUserDetails(UUID id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("Fetching details for user: {}", user.getEmail());

        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .createdAt(user.getCreatedAt())
                .role(user.getRole())
                .build(); // no token returned on view
    }


    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check email conflict only if it's being changed
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepo.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Email already in use");
            }
            user.setEmail(request.getEmail());
        }

        // Check phone conflict only if it's being changed
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
            if (userRepo.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
                throw new RuntimeException("Phone number already in use");
            }
            user.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }

        // Password change — requires current password verification
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null ||
                    !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new RuntimeException("Current password is incorrect");
            }
            if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
                throw new RuntimeException("New passwords do not match");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        user.setUpdatedAt(LocalDateTime.now());
        User updatedUser = userRepo.save(user);
        log.info("User updated: {}", updatedUser.getEmail());

        return UserResponse.builder()
                .id(updatedUser.getId())
                .fullName(updatedUser.getFullName())
                .email(updatedUser.getEmail())
                .phoneNumber(updatedUser.getPhoneNumber())
                .createdAt(updatedUser.getCreatedAt())
                .role(updatedUser.getRole())
                .build();
    }
}