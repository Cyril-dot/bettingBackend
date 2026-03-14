package com.bettingPlatform.BettingWebsite.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BulkBookGamesRequest {

    @NotEmpty(message = "Game IDs must not be empty")
    private List<UUID> gameIds;

    private boolean published;

    private boolean vipOnly;
}