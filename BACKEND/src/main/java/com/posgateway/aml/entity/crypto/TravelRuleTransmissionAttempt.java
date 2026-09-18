package com.posgateway.aml.entity.crypto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Immutable @Table(name = "travel_rule_transmission_attempts") @Getter @Setter
public class TravelRuleTransmissionAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "psp_id", nullable = false) private Long pspId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "travel_rule_transfer_id", nullable = false)
    private TravelRuleTransfer transfer;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Column(name = "idempotency_key", nullable = false, length = 160) private String idempotencyKey;
    @Column(length = 64) private String protocol;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "provider_message_id") private String providerMessageId;
    @Column(name = "response_code", length = 64) private String responseCode;
    @Column(name = "failure_reason", columnDefinition = "TEXT") private String failureReason;
    @Column(name = "attempted_at", nullable = false, updatable = false) private LocalDateTime attemptedAt;
    @Column(name = "retain_until", nullable = false, updatable = false) private LocalDate retainUntil;
    @PrePersist void create() { if (attemptedAt == null) attemptedAt = LocalDateTime.now(); if (retainUntil == null) retainUntil = attemptedAt.toLocalDate().plusYears(7); }
}
