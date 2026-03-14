package com.bettingPlatform.BettingWebsite.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "vip_price_table")
public class VipPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private Double price;
    private String currency;     // e.g. "GHS", "NGN", "USD"
    private String description;  // e.g. "24hr VIP Access"

    @Builder.Default
    private boolean active = true;

    private LocalDateTime updatedAt;
}