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
@Table(name = "game_odds_table")
public class GameOdds {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "game_id")
    private Game game;

    private String externalEventId;
    private String sportKey;
    private String bookmaker;

    // h2h odds
    private Double homeWinOdds;
    private Double drawOdds;
    private Double awayWinOdds;

    // totals
    private Double over15Odds;
    private Double over25Odds;
    private Double under25Odds;
    private Double over35Odds;

    private LocalDateTime lastUpdated;
}