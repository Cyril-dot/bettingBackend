package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminRepo extends JpaRepository<Admin, UUID> {

    // Auth lookups
    Optional<Admin> findByEmail(String email);

    // Existence checks (registration validation)
    boolean existsByEmail(String email);
}