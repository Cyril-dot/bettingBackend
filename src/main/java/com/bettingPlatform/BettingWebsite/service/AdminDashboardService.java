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

    private final PromoRepo              promoRepo;
    private final VipPriceRepo           vipPriceRepo;
    private final VipSubscriptionRepo    vipSubscriptionRepo;
    private final UserRepo               userRepo;
    private final AiAnalyticsService     aiAnalyticsService;
    private final CloudinaryService      cloudinaryService;
    private final CurrencyConverter      currencyConverter;


    // ── VIP Price ─────────────────────────────────────────────────

    public VipPriceResponse setVipPrice(SetVipPriceRequest request) {
        String currency = request.getCurrency().toUpperCase();
        if (!CurrencyConverter.SUPPORTED_BASE_CURRENCIES.contains(currency)) {
            throw new RuntimeException(
                    "Unsupported currency: " + currency +
                            ". Allowed: " + CurrencyConverter.SUPPORTED_BASE_CURRENCIES);
        }
        if (request.getPrice() == null || request.getPrice() <= 0) {
            throw new RuntimeException("VIP price must be greater than zero.");
        }

        vipPriceRepo.findByActiveTrue().ifPresent(existing -> {
            existing.setActive(false);
            vipPriceRepo.save(existing);
            log.info("🔄 Previous VIP price ({} {}) deactivated",
                    existing.getPrice(), existing.getCurrency());
        });

        VipPrice price = VipPrice.builder()
                .price(request.getPrice())
                .currency(currency)
                .description(request.getDescription())
                .active(true)
                .updatedAt(LocalDateTime.now())
                .build();

        VipPrice saved = vipPriceRepo.save(price);

        double ghsEquiv = currencyConverter.convert(saved.getPrice(), currency, "GHS");
        double ngnEquiv = currencyConverter.convert(saved.getPrice(), currency, "NGN");
        double usdEquiv = currencyConverter.convert(saved.getPrice(), currency, "USD");

        log.info("💰 VIP price set: {} {}  ≈  {} GHS  |  {} NGN  |  {} USD",
                saved.getPrice(), currency, ghsEquiv, ngnEquiv, usdEquiv);

        return buildVipPriceResponse(saved);
    }

    public VipPriceResponse getCurrentVipPrice() {
        VipPrice price = vipPriceRepo.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("No active VIP price configured."));
        return buildVipPriceResponse(price);
    }


    // ── Promos ────────────────────────────────────────────────────

    public PromoResponse createPromo(CreatePromoRequest request,
                                     MultipartFile image) throws IOException {

        LocalDateTime startsAt;
        LocalDateTime expiresAt;
        try {
            startsAt  = parseFlexibleDateTime(request.getStartsAt());
            expiresAt = parseFlexibleDateTime(request.getExpiresAt());
        } catch (Exception e) {
            throw new RuntimeException("Invalid date format. Expected YYYY-MM-DDTHH:MM or YYYY-MM-DDTHH:MM:SS");
        }

        if (!startsAt.isBefore(expiresAt)) {
            throw new RuntimeException("Expiry time must be after start time.");
        }

        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            Map uploadResult = cloudinaryService.uploadImage(image, "bettingPlatform/promos");
            imageUrl = (String) uploadResult.get("secure_url");
            log.info("📸 Promo image uploaded: {}", imageUrl);
        }

        Promo promo = Promo.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .imageUrl(imageUrl)
                .type(request.getType())
                .discountPercent(request.getDiscountPercent())
                .startsAt(startsAt)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Promo saved = promoRepo.save(promo);
        log.info("🎉 Promo created: {}", saved.getTitle());
        return mapPromo(saved);
    }

    /**
     * ADMIN — returns ALL promos (upcoming + active + expired).
     * This is what the admin dashboard displays.
     */
    public List<PromoResponse> getAllPromos() {
        return promoRepo.findAllPromos()
                .stream().map(this::mapPromo).collect(Collectors.toList());
    }

    /**
     * PUBLIC — returns only currently active promos (date window check).
     * This is what the user-facing app displays.
     */
    public List<PromoResponse> getActivePromos() {
        return promoRepo.findActivePromos(LocalDateTime.now())
                .stream().map(this::mapPromo).collect(Collectors.toList());
    }

    public void deletePromo(UUID id) throws IOException {
        Promo promo = promoRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Promo not found: " + id));

        if (promo.getImageUrl() != null) {
            String publicId = extractPublicId(promo.getImageUrl());
            cloudinaryService.deleteImage(publicId);
            log.info("🗑️ Promo image deleted from Cloudinary: {}", publicId);
        }

        promoRepo.deleteById(id);
        log.info("🗑️ Promo deleted: {}", id);
    }

    private LocalDateTime parseFlexibleDateTime(String raw) {
        if (raw == null || raw.isBlank()) throw new RuntimeException("Date cannot be blank");
        String normalized = raw.length() == 16 ? raw + ":00" : raw;
        return LocalDateTime.parse(normalized);
    }

    private String extractPublicId(String imageUrl) {
        try {
            String[] parts      = imageUrl.split("/upload/");
            String afterUpload  = parts[1];
            String withoutVer   = afterUpload.replaceFirst("v\\d+/", "");
            return withoutVer.substring(0, withoutVer.lastIndexOf("."));
        } catch (Exception e) {
            log.warn("⚠️ Could not extract public_id from: {}", imageUrl);
            return imageUrl;
        }
    }


    // ── Dashboard stats ───────────────────────────────────────────

    public DashboardStatsResponse getDashboardStats() {
        long totalUsers = userRepo.count();
        long activeVip  = vipSubscriptionRepo.countByActiveTrue();
        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeVipUsers(activeVip)
                .build();
    }


    // ── Private helpers ───────────────────────────────────────────

    private VipPriceResponse buildVipPriceResponse(VipPrice price) {
        return VipPriceResponse.builder()
                .id(price.getId())
                .price(price.getPrice())
                .currency(price.getCurrency())
                .description(price.getDescription())
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