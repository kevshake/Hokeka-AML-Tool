package com.posgateway.aml.entity.crypto;

import com.posgateway.aml.entity.multiasset.MultiAssetTransaction;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity @Immutable @Table(name = "wallet_screening_records") @Getter @Setter
public class WalletScreeningRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "psp_id", nullable = false) private Long pspId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "wallet_profile_id")
    private CryptoWalletProfile walletProfile;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private MultiAssetCustomer customer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "transaction_id") private MultiAssetTransaction transaction;
    @Column(name = "screened_address", length = 512) private String screenedAddress;
    @Column(length = 64) private String network;
    @Enumerated(EnumType.STRING) @Column(name = "trigger_type", nullable = false, length = 32)
    private WalletScreeningTrigger triggerType;
    @Column(nullable = false, length = 128) private String provider;
    @Column(name = "provider_reference") private String providerReference;
    @Column(nullable = false) private boolean available;
    @Column(name = "risk_score") private Integer riskScore;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false) private List<String> categories = new ArrayList<>();
    @Column(name = "direct_exposure_percent", precision = 8, scale = 5) private BigDecimal directExposurePercent;
    @Column(name = "indirect_exposure_percent", precision = 8, scale = 5) private BigDecimal indirectExposurePercent;
    @Column(name = "maximum_exposure_depth") private Integer maximumExposureDepth;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> attributions = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> evidence = new LinkedHashMap<>();
    @Column(name = "unavailable_reason", columnDefinition = "TEXT") private String unavailableReason;
    @Column(name = "screened_at", nullable = false, updatable = false) private LocalDateTime screenedAt;
    @Column(name = "retain_until", nullable = false, updatable = false) private LocalDate retainUntil;
    @PrePersist void create() { if (screenedAt == null) screenedAt = LocalDateTime.now(); if (retainUntil == null) retainUntil = screenedAt.toLocalDate().plusYears(7); }
}
