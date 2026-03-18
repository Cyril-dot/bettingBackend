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
    private final CurrencyConverter      currencyConverter;   // ← injected


    // ── VIP Price ─────────────────────────────────────────────────

    /**
     * Admin sets the VIP price in ANY supported currency (USD, GHS, NGN, EUR, GBP).
     * The price is stored as-is; conversion to each user's local currency
     * happens at payment time inside PaystackService.
     *
     * Validation:
     *   - Currency must be in CurrencyConverter.SUPPORTED_BASE_CURRENCIES
     *   - Price must be > 0
     */
    public VipPriceResponse setVipPrice(SetVipPriceRequest request) {
        // Validate currency
        String currency = request.getCurrency().toUpperCase();
        if (!CurrencyConverter.SUPPORTED_BASE_CURRENCIES.contains(currency)) {
            throw new RuntimeException(
                    "Unsupported currency: " + currency +
                            ". Allowed: " + CurrencyConverter.SUPPORTED_BASE_CURRENCIES);
        }

        // Validate price
        if (request.getPrice() == null || request.getPrice() <= 0) {
            throw new RuntimeException("VIP price must be greater than zero.");
        }

        // Deactivate existing active price
        vipPriceRepo.findByActiveTrue().ifPresent(existing -> {
            existing.setActive(false);
            vipPriceRepo.save(existing);
            log.info("🔄 Previous VIP price ({} {}) deactivated",
                    existing.getPrice(), existing.getCurrency());
        });

        VipPrice price = VipPrice.builder()
                .price(request.getPrice())
                .currency(currency)                    // stored exactly as admin chose
                .description(request.getDescription())
                .active(true)
                .updatedAt(LocalDateTime.now())
                .build();

        VipPrice saved = vipPriceRepo.save(price);

        // Log equivalent amounts in all supported Paystack currencies for reference
        double ghsEquiv = currencyConverter.convert(saved.getPrice(), currency, "GHS");
        double ngnEquiv = currencyConverter.convert(saved.getPrice(), currency, "NGN");
        double usdEquiv = currencyConverter.convert(saved.getPrice(), currency, "USD");

        log.info("💰 VIP price set: {} {}  ≈  {} GHS  |  {} NGN  |  {} USD",
                saved.getPrice(), currency, ghsEquiv, ngnEquiv, usdEquiv);

        return buildVipPriceResponse(saved);
    }

    /**
     * Returns the current active VIP price (as the admin set it — no conversion here).
     * Frontend should call PaystackService.getVipPriceForUser() for user-facing prices.
     */
    public VipPriceResponse getCurrentVipPrice() {
        VipPrice price = vipPriceRepo.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("No active VIP price configured."));
        return buildVipPriceResponse(price);
    }


    // ── Promos ────────────────────────────────────────────────────

    public PromoResponse createPromo(CreatePromoRequest request,
                                     MultipartFile image) throws IOException {

        // Parse date strings manually — works with both "YYYY-MM-DDTHH:MM" and "YYYY-MM-DDTHH:MM:SS"
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
     * Accepts both "YYYY-MM-DDTHH:MM" (from datetime-local input)
     * and "YYYY-MM-DDTHH:MM:SS" (ISO-8601 with seconds).
     */
    private LocalDateTime parseFlexibleDateTime(String raw) {
        if (raw == null || raw.isBlank()) throw new RuntimeException("Date cannot be blank");
        // Append seconds if missing (datetime-local gives 16-char string)
        String normalized = raw.length() == 16 ? raw + ":00" : raw;
        return LocalDateTime.parse(normalized); // uses ISO_LOCAL_DATE_TIME by default
    }

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

    private String extractPublicId(String imageUrl) {
        // e.g. https://res.cloudinary.com/cloud/image/upload/v123/bettingPlatform/promos/abc.jpg
        //   →  bettingPlatform/promos/abc
        try {
            String[] parts      = imageUrl.split("/upload/");
            String afterUpload  = parts[1];                              // v123/bettingPlatform/promos/abc.jpg
            String withoutVer   = afterUpload.replaceFirst("v\\d+/", ""); // bettingPlatform/promos/abc.jpg
            return withoutVer.substring(0, withoutVer.lastIndexOf("."));  // remove extension
        } catch (Exception e) {
            log.warn("⚠️ Could not extract public_id from: {}", imageUrl);
            return imageUrl;
        }
    }


    // ── Dashboard stats ───────────────────────────────────────────

    public DashboardStatsResponse getDashboardStats() {
        long totalUsers  = userRepo.count();
        long activeVip   = vipSubscriptionRepo.countByActiveTrue();

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