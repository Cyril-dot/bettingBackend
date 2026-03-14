package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.LastLogin;
import com.bettingPlatform.BettingWebsite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LastLoginRepo extends JpaRepository<LastLogin, UUID> {
    Optional<LastLogin> findByUser(User user);
}