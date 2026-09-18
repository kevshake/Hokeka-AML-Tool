package com.posgateway.aml.entity.multiasset;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "asset_accounts", uniqueConstraints =
        @UniqueConstraint(name = "uq_asset_account", columnNames = {"psp_id", "external_account_id"}))
@Getter
@Setter
public class AssetAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private MultiAssetCustomer customer;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "external_account_id", nullable = false, length = 160)
    private String externalAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 24)
    private AssetClass assetClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_domain", nullable = false, length = 48)
    private ProductDomain productDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private AssetAccountType accountType;

    @Column(name = "provider_name", length = 128)
    private String providerName;

    @Column(length = 12)
    private String currency;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "public_address")
    private String publicAddress;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

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
