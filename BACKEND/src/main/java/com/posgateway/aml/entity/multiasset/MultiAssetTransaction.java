package com.posgateway.aml.entity.multiasset;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "multi_asset_transactions", uniqueConstraints =
        @UniqueConstraint(name = "uq_multi_asset_transaction", columnNames = {"psp_id", "external_transaction_id"}))
@Getter
@Setter
public class MultiAssetTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "external_transaction_id", nullable = false, length = 160)
    private String externalTransactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private MultiAssetCustomer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private AssetAccount sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private AssetAccount destinationAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 24)
    private AssetClass assetClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_domain", nullable = false, length = 48)
    private ProductDomain productDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_product_domain", length = 48)
    private ProductDomain sourceProductDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_product_domain", length = 48)
    private ProductDomain destinationProductDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private MultiAssetTransactionType transactionType;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal amount;

    @Column(name = "fiat_equivalent_usd", precision = 24, scale = 8)
    private BigDecimal fiatEquivalentUsd;

    @Column(nullable = false, length = 12)
    private String currency;

    @Column(name = "asset_symbol", length = 32)
    private String assetSymbol;

    @Column(precision = 30, scale = 12)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 24, scale = 8)
    private BigDecimal unitPrice;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "counterparty_reference")
    private String counterpartyReference;

    @Column(name = "funding_party_customer_id", length = 128)
    private String fundingPartyCustomerId;

    @Column(name = "source_network", length = 64)
    private String sourceNetwork;

    @Column(name = "destination_network", length = 64)
    private String destinationNetwork;

    @Column(name = "originator_name")
    private String originatorName;

    @Column(name = "originator_account")
    private String originatorAccount;

    @Column(name = "beneficiary_name")
    private String beneficiaryName;

    @Column(name = "beneficiary_account")
    private String beneficiaryAccount;

    @Column(name = "merchant_category", length = 128)
    private String merchantCategory;

    @Column(name = "travel_rule_required", nullable = false)
    private boolean travelRuleRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_rule_status", nullable = false, length = 24)
    private TravelRuleStatus travelRuleStatus = TravelRuleStatus.NOT_REQUIRED;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskDecision decision = RiskDecision.ALLOW;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (executedAt == null) executedAt = LocalDateTime.now();
    }
}
