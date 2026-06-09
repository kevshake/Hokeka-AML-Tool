package com.posgateway.aml.entity.chargeback;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "chargeback_disputes")
@Data
public class ChargebackDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_event_id")
    private String externalEventId;

    @Column(name = "deduplication_id")
    private String deduplicationId;

    @Column(name = "notification_type", nullable = false)
    private String notificationType;

    @Column(name = "rdr_status")
    private String rdrStatus;

    @Column(name = "scheme")
    private String scheme = "visa";

    @Column(name = "case_id")
    private String caseId;

    @Column(name = "case_date")
    private LocalDate caseDate;

    @Column(name = "case_amount")
    private BigDecimal caseAmount;

    @Column(name = "case_currency")
    private String caseCurrency;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "reason_category")
    private String reasonCategory;

    @Column(name = "acquirer_reference_number")
    private String acquirerReferenceNumber;

    @Column(name = "network_merchant_id")
    private String networkMerchantId;

    @Column(name = "network_transaction_id")
    private String networkTransactionId;

    @Column(name = "merchant_order_id")
    private String merchantOrderId;

    @Column(name = "psp_transaction_id")
    private String pspTransactionId;

    @Column(name = "card_bin")
    private String cardBin;

    @Column(name = "card_last4")
    private String cardLast4;

    @Column(name = "refunded")
    private Boolean refunded = false;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "compliance_case_id")
    private Long complianceCaseId;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
