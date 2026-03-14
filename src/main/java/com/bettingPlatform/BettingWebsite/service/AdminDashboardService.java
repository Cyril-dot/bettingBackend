package com.bettingPlatform.BettingWebsite.service;

import com.bettingPlatform.BettingWebsite.dto.*;
import com.bettingPlatform.BettingWebsite.entity.*;
import com.bettingPlatform.BettingWebsite.entity.repos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {

    private final PromoRepo promoRepo;
    private final VipPriceRepo vipPriceRepo;
    private final VipSubscriptionRepo vipSubscriptionRepo;
    private final UserRepo userRepo;
    private final AiAnalyticsService aiAnalyticsService;
    private final CloudinaryService cloudinaryService;


    // ── VIP Price ─────────────────────────────────────────────────
    public VipPriceResponse setVipPrice(SetVipPriceRequest request) {
        // deactivate existing price
        vipPriceRepo.findByActiveTrue().ifPresent(existing -> {
            existing.setActive(false);
            vipPriceRepo.save(existing);
        });

        VipPrice price = VipPrice.builder()
                .price(request.getPrice())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .updatedAt(LocalDateTime.now())
                .build();

        VipPrice saved = vipPriceRepo.save(price);
        log.info("💰 VIP price set to {} {}", saved.getPrice(), saved.getCurrency());

        return VipPriceResponse.builder()
                .id(saved.getId())
                .price(saved.getPrice())
                .currency(saved.getCurrency())
                .description(saved.getDescription())
                .build();
    }

    public VipPriceResponse getCurrentVipPrice() {
        VipPrice price = vipPriceRepo.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("No VIP price set"));
        return VipPriceResponse.builder()
                .id(price.getId())
                .price(price.getPrice())
                .currency(price.getCurrency())
                .description(price.getDescription())
                .build();
    }


    // ── Promos ────────────────────────────────────────────────────
    public PromoResponse createPromo(CreatePromoRequest request,
                                     MultipartFile image) throws IOException {

        // Upload image to Cloudinary if provided
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            Map uploadResult = cloudinaryService.uploadImage(
                    image, "bettingPlatform/promos");
            imageUrl = (String) uploadResult.get("secure_url");
            log.info("📸 Promo image uploaded: {}", imageUrl);
        }

        Promo promo = Promo.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .imageUrl(imageUrl)            // ← Cloudinary URL saved here
                .type(request.getType())
                .discountPercent(request.getDiscountPercent())
                .startsAt(request.getStartsAt())
                .expiresAt(request.getExpiresAt())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Promo saved = promoRepo.save(promo);
        log.info("🎉 Promo created: {}", saved.getTitle());
        return mapPromo(saved);
    }
    public List<PromoResponse> getActivePromos() {
        return promoRepo.findActivePromos(LocalDateTime.now())
                .stream().map(this::mapPromo).collect(Collectors.toList());
    }

    public void deletePromo(UUID id) throws IOException {
        Promo promo = promoRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Promo not found"));

        // Delete image from Cloudinary if exists
        if (promo.getImageUrl() != null) {
            // Extract public_id from Cloudinary URL
            // URL format: .../bettingPlatform/promos/filename
            String publicId = extractPublicId(promo.getImageUrl());
            cloudinaryService.deleteImage(publicId);
            log.info("🗑️ Promo image deleted from Cloudinary: {}", publicId);
        }

        promoRepo.deleteById(id);
        log.info("🗑️ Promo deleted: {}", id);
    }

    // Extracts public_id from Cloudinary URL
    private String extractPublicId(String imageUrl) {
        // e.g. https://res.cloudinary.com/cloud/image/upload/v123/bettingPlatform/promos/abc.jpg
        // → bettingPlatform/promos/abc
        try {
            String[] parts = imageUrl.split("/upload/");
            String afterUpload = parts[1]; // v123/bettingPlatform/promos/abc.jpg
            String withoutVersion = afterUpload.replaceFirst("v\\d+/", "");
            return withoutVersion.substring(0, withoutVersion.lastIndexOf(".")); // remove extension
        } catch (Exception e) {
            log.warn("⚠️ Could not extract public_id from: {}", imageUrl);
            return imageUrl;
        }
    }



    // ── Dashboard stats ───────────────────────────────────────────
    public DashboardStatsResponse getDashboardStats() {
        long totalUsers = userRepo.count();
        long activeVip = vipSubscriptionRepo.countByActiveTrue();
        long totalPredictions = 0; // inject PredictionRepo if needed

        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeVipUsers(activeVip)
                .build();
    }


    private PromoResponse mapPromo(Promo p) {
        return PromoResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .description(p.getDescription())
                .imageUrl(p.getImageUrl())
                .type(p.getType())
                .discountPercent(p.getDiscountPercent())
                .startsAt(p.getStartsAt())
                .expiresAt(p.getExpiresAt())
                .active(p.isActive())
                .build();
    }
}