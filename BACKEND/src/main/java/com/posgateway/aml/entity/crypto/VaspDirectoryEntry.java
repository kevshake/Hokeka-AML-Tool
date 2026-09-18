package com.posgateway.aml.entity.crypto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "vasp_directory_entries", uniqueConstraints = @UniqueConstraint(
        name = "uq_vasp_directory_licence", columnNames = {"psp_id", "jurisdiction", "licence_number"}))
@Getter
@Setter
public class VaspDirectoryEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "psp_id", nullable = false) private Long pspId;
    @Column(name = "legal_name", nullable = false) private String legalName;
    @Column(name = "trading_name") private String tradingName;
    @Column(nullable = false, length = 3) private String jurisdiction;
    @Column(name = "regulator_name") private String regulatorName;
    @Column(name = "licence_number", length = 160) private String licenceNumber;
    @Enumerated(EnumType.STRING) @Column(name = "licence_status", nullable = false, length = 32)
    private VaspLicenseStatus licenceStatus = VaspLicenseStatus.UNKNOWN;
    @Column(name = "licence_valid_until") private LocalDate licenceValidUntil;
    @Column(name = "registration_number", length = 160) private String registrationNumber;
    @Column(length = 500) private String website;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "travel_rule_protocols", columnDefinition = "jsonb", nullable = false)
    private List<String> travelRuleProtocols = new ArrayList<>();
    @Column(name = "sanctions_status", nullable = false, length = 32) private String sanctionsStatus = "NOT_SCREENED";
    @Column(name = "sanctions_screened_at") private LocalDateTime sanctionsScreenedAt;
    @Column(name = "sanctions_provider", length = 128) private String sanctionsProvider;
    @Column(name = "sanctions_match_count", nullable = false) private int sanctionsMatchCount;
    @Column(name = "sanctions_next_screening_at", nullable = false) private LocalDateTime sanctionsNextScreeningAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "beneficial_ownership", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> beneficialOwnership = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "wallet_clusters", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> walletClusters = new ArrayList<>();
    @Column(name = "risk_score", nullable = false) private int riskScore;
    @Column(name = "risk_level", nullable = false, length = 16) private String riskLevel = "LOW";
    @Enumerated(EnumType.STRING) @Column(name = "transfer_decision", nullable = false, length = 24)
    private VaspTransferDecision transferDecision = VaspTransferDecision.REVIEW;
    @Column(name = "review_notes", columnDefinition = "TEXT") private String reviewNotes;
    @Column(name = "last_reviewed_at") private LocalDateTime lastReviewedAt;
    @Column(name = "next_review_at") private LocalDateTime nextReviewAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; if (sanctionsNextScreeningAt == null) sanctionsNextScreeningAt = now; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
