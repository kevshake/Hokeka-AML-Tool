package com.posgateway.aml.entity.mobilemoney;

import com.posgateway.aml.entity.multiasset.MultiAssetTransaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "mobile_money_risk_profiles", uniqueConstraints = @UniqueConstraint(
        name = "uq_mobile_money_risk_profile", columnNames = {"psp_id", "entity_type", "entity_reference"}))
@Getter
@Setter
public class MobileMoneyRiskProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32)
    private MobileMoneyEntityType entityType;

    @Column(name = "entity_reference", nullable = false)
    private String entityReference;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel = "LOW";

    @Column(name = "signal_count", nullable = false)
    private long signalCount;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "last_assessed_at", nullable = false)
    private LocalDateTime lastAssessedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_transaction_id")
    private MultiAssetTransaction latestTransaction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

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
