package com.posgateway.aml.entity.crypto;

import com.posgateway.aml.entity.multiasset.AssetAccount;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "crypto_wallet_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_crypto_wallet_profile", columnNames = {"psp_id", "network", "wallet_address"}),
        @UniqueConstraint(name = "uq_crypto_wallet_account", columnNames = {"psp_id", "asset_account_id"})})
@Getter
@Setter
public class CryptoWalletProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "psp_id", nullable = false) private Long pspId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asset_account_id", nullable = false)
    private AssetAccount assetAccount;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    private MultiAssetCustomer customer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vasp_id") private VaspDirectoryEntry vasp;
    @Column(name = "wallet_address", nullable = false, length = 512) private String walletAddress;
    @Column(nullable = false, length = 64) private String network;
    @Column(name = "address_label") private String addressLabel;
    @Column(name = "ownership_type", nullable = false, length = 24) private String ownershipType = "CUSTOMER";
    @Column(nullable = false, length = 24) private String status = "ACTIVE";
    @Column(name = "screening_interval_hours", nullable = false) private int screeningIntervalHours = 24;
    @Column(name = "last_screened_at") private LocalDateTime lastScreenedAt;
    @Column(name = "next_screening_at", nullable = false) private LocalDateTime nextScreeningAt;
    @Column(name = "latest_risk_score") private Integer latestRiskScore;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "latest_categories", columnDefinition = "jsonb", nullable = false)
    private List<String> latestCategories = new ArrayList<>();
    @Column(name = "latest_provider", length = 128) private String latestProvider;
    @Column(name = "latest_provider_reference") private String latestProviderReference;
    @Column(name = "screening_available", nullable = false) private boolean screeningAvailable;
    @Column(name = "screening_status", nullable = false, length = 24) private String screeningStatus = "PENDING";
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; if (nextScreeningAt == null) nextScreeningAt = now; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
