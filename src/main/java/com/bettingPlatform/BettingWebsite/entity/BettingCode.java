package com.bettingPlatform.BettingWebsite.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "betting_codes_table")
public class BettingCode {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String bookmaker;     // e.g. "SportyBet", "Bet9ja", "1xBet"
    private String code;          // e.g. "8W1HLE"
    private String description;   // e.g. "10 games accumulator - High Risk"
    private Double potentialOdds;

    @Enumerated(EnumType.STRING)
    private PredictionType type;   // FREE, VIP

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BettingCodeStatus status = BettingCodeStatus.ACTIVE;

    private LocalDate validDate;

    @Builder.Default
    private boolean published = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}