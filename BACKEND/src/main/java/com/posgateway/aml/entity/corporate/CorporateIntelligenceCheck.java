package com.posgateway.aml.entity.corporate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.posgateway.aml.entity.merchant.Merchant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "corporate_intelligence_checks", indexes = {
        @Index(name = "idx_corporate_intelligence_psp_checked", columnList = "psp_id,checked_at"),
        @Index(name = "idx_corporate_intelligence_merchant_checked", columnList = "merchant_id,checked_at"),
        @Index(name = "idx_corporate_intelligence_status", columnList = "psp_id,status")
})
@Getter
@Setter
public class CorporateIntelligenceCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "check_type", nullable = false, length = 32)
    private String checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CorporateIntelligenceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "registry_status", nullable = false, length = 24)
    private CorporateRegistryStatus registryStatus;

    @Column(name = "registry_provider", nullable = false, length = 100)
    private String registryProvider;

    @Column(name = "registry_match_score", nullable = false)
    private int registryMatchScore;

    @Column(name = "matched_company_name", length = 500)
    private String matchedCompanyName;

    @Column(name = "matched_company_number", length = 160)
    private String matchedCompanyNumber;

    @Column(name = "matched_jurisdiction", length = 100)
    private String matchedJurisdiction;

    @Column(name = "matched_company_status", length = 100)
    private String matchedCompanyStatus;

    @Column(name = "matched_company_url", length = 1000)
    private String matchedCompanyUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "registry_candidates", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> registryCandidates = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "registry_provenance", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> registryProvenance = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "adverse_media_status", nullable = false, length = 24)
    private AdverseMediaStatus adverseMediaStatus;

    @Column(name = "adverse_media_provider", nullable = false, length = 100)
    private String adverseMediaProvider;

    @Column(name = "adverse_media_query", columnDefinition = "TEXT")
    private String adverseMediaQuery;

    @Column(name = "adverse_media_article_count", nullable = false)
    private int adverseMediaArticleCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adverse_media_articles", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> adverseMediaArticles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adverse_media_provenance", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> adverseMediaProvenance = new LinkedHashMap<>();

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "decision_reason", nullable = false, columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "evidence_hash", nullable = false, length = 64)
    private String evidenceHash;

    @Column(name = "checked_by", nullable = false, length = 160)
    private String checkedBy;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private LocalDateTime checkedAt;

    @Column(name = "retain_until", nullable = false, updatable = false)
    private LocalDate retainUntil;

    @PrePersist
    void create() {
        if (checkedAt == null) checkedAt = LocalDateTime.now();
        if (retainUntil == null) retainUntil = checkedAt.toLocalDate().plusYears(7);
    }
}
