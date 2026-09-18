package com.posgateway.aml.entity.multiasset;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "multi_asset_customers", uniqueConstraints =
        @UniqueConstraint(name = "uq_multi_asset_customer", columnNames = {"psp_id", "external_customer_id"}))
@Getter
@Setter
public class MultiAssetCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "external_customer_id", nullable = false, length = 128)
    private String externalCustomerId;

    @Column(name = "customer_type", nullable = false, length = 24)
    private String customerType;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "risk_tier", nullable = false, length = 20)
    private String riskTier = "MEDIUM";

    @Column(name = "kyc_status", nullable = false, length = 24)
    private String kycStatus = "PENDING";

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
