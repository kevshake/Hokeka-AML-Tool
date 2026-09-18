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
@Table(name = "multi_asset_risk_signals")
@Getter
@Setter
public class MultiAssetRiskSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private MultiAssetTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private MultiAssetCustomer customer;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "signal_code", nullable = false, length = 80)
    private String signalCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 32)
    private FinancialCrimeSignalType signalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_domain", nullable = false, length = 48)
    private ProductDomain productDomain;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(name = "score_impact", nullable = false)
    private int scoreImpact;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> evidence = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
