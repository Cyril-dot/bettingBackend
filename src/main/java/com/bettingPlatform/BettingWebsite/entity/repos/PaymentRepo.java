package com.bettingPlatform.BettingWebsite.entity.repos;

import com.bettingPlatform.BettingWebsite.entity.Payment;
import com.bettingPlatform.BettingWebsite.entity.PaymentStatus;
import com.bettingPlatform.BettingWebsite.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepo extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByReference(String reference);
    List<Payment> findByUser(User user);
    List<Payment> findByUserAndStatus(User user, PaymentStatus status);
    boolean existsByReferenceAndStatus(String reference, PaymentStatus status);
}