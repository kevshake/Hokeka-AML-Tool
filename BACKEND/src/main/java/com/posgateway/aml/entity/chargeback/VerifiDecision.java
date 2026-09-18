package com.posgateway.aml.entity.chargeback;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "verifi_decisions")
@lombok.Getter
@lombok.Setter
public class VerifiDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id")
    private String caseId;

    @Column(name = "psp_transaction_id")
    private String pspTransactionId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "decision", nullable = false, length = 16)
    private String decision;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
