package com.posgateway.aml.entity.crypto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "travel_rule_jurisdiction_policies", uniqueConstraints = @UniqueConstraint(
        name = "uq_travel_rule_policy", columnNames = {"psp_id", "policy_code"})) @Getter @Setter
public class TravelRuleJurisdictionPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "psp_id", nullable = false) private Long pspId;
    @Column(nullable = false, length = 3) private String jurisdiction;
    @Column(name = "policy_code", nullable = false, length = 80) private String policyCode;
    @Column(name = "threshold_usd", nullable = false, precision = 24, scale = 8) private BigDecimal thresholdUsd = BigDecimal.ZERO;
    @Column(name = "applies_to_all_transfers", nullable = false) private boolean appliesToAllTransfers;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "required_fields", columnDefinition = "jsonb", nullable = false)
    private List<String> requiredFields = new ArrayList<>();
    @Column(name = "verify_originator", nullable = false) private boolean verifyOriginator = true;
    @Column(name = "verify_beneficiary", nullable = false) private boolean verifyBeneficiary = true;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "accepted_protocols", columnDefinition = "jsonb", nullable = false)
    private List<String> acceptedProtocols = new ArrayList<>();
    @Column(name = "retention_years", nullable = false) private int retentionYears = 7;
    @Column(name = "effective_from", nullable = false) private LocalDateTime effectiveFrom;
    @Column(name = "effective_until") private LocalDateTime effectiveUntil;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "legal_reference", length = 1000) private String legalReference;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; if (effectiveFrom == null) effectiveFrom = now; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
