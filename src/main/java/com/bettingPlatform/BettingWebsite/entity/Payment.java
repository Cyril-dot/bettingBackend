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
@Table(name = "payments_table")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String reference;        // Paystack reference e.g. "PAY_abc123"
    private String paystackId;       // Paystack transaction ID

    private Double amount;           // in kobo/pesewas — multiply by 100
    private String currency;         // "GHS" or "NGN"

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentPurpose purpose = PaymentPurpose.VIP_SUBSCRIPTION;  // VIP_SUBSCRIPTION

    private String email;            // user email used for payment
    private String channel;          // "card", "mobile_money" etc
    private String gatewayResponse;  // Paystack's response message

    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}