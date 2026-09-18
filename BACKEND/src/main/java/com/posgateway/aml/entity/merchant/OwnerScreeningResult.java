package com.posgateway.aml.entity.merchant;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Beneficial Owner screening result entity
 */
@Entity
@Table(name = "owner_screening_results")
public class OwnerScreeningResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_id")
    private Long screeningId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private BeneficialOwner owner;

    // Screening Details
    @Column(name = "screening_type", nullable = false, length = 50)
    private String screeningType; // ONBOARDING, PERIODIC, UPDATE, MANUAL

    @Column(name = "screening_status", nullable = false, length = 50)
    private String screeningStatus; // CLEAR, MATCH, POTENTIAL_MATCH

    @Column(name = "match_score", precision = 5, scale = 4)
    private BigDecimal matchScore;

    @Column(name = "match_count")
    private Integer matchCount = 0;

    @Column(name = "is_pep")
    private Boolean isPep = false;

    @Column(name = "is_sanctioned")
    private Boolean isSanctioned = false;

    // Results (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_details", columnDefinition = "jsonb")
    private Map<String, Object> matchDetails;

    @Column(name = "screening_provider", length = 100)
    private String screeningProvider; // INTERNAL_AEROSPIKE, SUMSUB

    // Metadata
    @Column(name = "screened_at", nullable = false)
    private LocalDateTime screenedAt = LocalDateTime.now();

    @Column(name = "screened_by", length = 100)
    private String screenedBy;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public OwnerScreeningResult() {
    }

    public OwnerScreeningResult(Long screeningId, BeneficialOwner owner, String screeningType, String screeningStatus,
            BigDecimal matchScore, Integer matchCount, Boolean isPep, Boolean isSanctioned,
            Map<String, Object> matchDetails, String screeningProvider, LocalDateTime screenedAt, String screenedBy,
            String notes) {
        this.screeningId = screeningId;
        this.owner = owner;
        this.screeningType = screeningType;
        this.screeningStatus = screeningStatus;
        this.matchScore = matchScore;
        this.matchCount = matchCount != null ? matchCount : 0;
        this.isPep = isPep != null ? isPep : false;
        this.isSanctioned = isSanctioned != null ? isSanctioned : false;
        this.matchDetails = matchDetails;
        this.screeningProvider = screeningProvider;
        this.screenedAt = screenedAt != null ? screenedAt : LocalDateTime.now();
        this.screenedBy = screenedBy;
        this.notes = notes;
    }

    public Long getScreeningId() {
        return screeningId;
    }

    public void setScreeningId(Long screeningId) {
        this.screeningId = screeningId;
    }

    public BeneficialOwner getOwner() {
        return owner;
    }

    public void setOwner(BeneficialOwner owner) {
        this.owner = owner;
    }

    public String getScreeningType() {
        return screeningType;
    }

    public void setScreeningType(String screeningType) {
        this.screeningType = screeningType;
    }

    public String getScreeningStatus() {
        return screeningStatus;
    }

    public void setScreeningStatus(String screeningStatus) {
        this.screeningStatus = screeningStatus;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(BigDecimal matchScore) {
        this.matchScore = matchScore;
    }

    public Integer getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(Integer matchCount) {
        this.matchCount = matchCount;
    }

    public Boolean getIsPep() {
        return isPep;
    }

    public void setIsPep(Boolean isPep) {
        this.isPep = isPep;
    }

    public Boolean getIsSanctioned() {
        return isSanctioned;
    }

    public void setIsSanctioned(Boolean isSanctioned) {
        this.isSanctioned = isSanctioned;
    }

    public Map<String, Object> getMatchDetails() {
        return matchDetails;
    }

    public void setMatchDetails(Map<String, Object> matchDetails) {
        this.matchDetails = matchDetails;
    }

    public String getScreeningProvider() {
        return screeningProvider;
    }

    public void setScreeningProvider(String screeningProvider) {
        this.screeningProvider = screeningProvider;
    }

    public LocalDateTime getScreenedAt() {
        return screenedAt;
    }

    public void setScreenedAt(LocalDateTime screenedAt) {
        this.screenedAt = screenedAt;
    }

    public String getScreenedBy() {
        return screenedBy;
    }

    public void setScreenedBy(String screenedBy) {
        this.screenedBy = screenedBy;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public static OwnerScreeningResultBuilder builder() {
        return new OwnerScreeningResultBuilder();
    }

    public static class OwnerScreeningResultBuilder {
        private Long screeningId;
        private BeneficialOwner owner;
        private String screeningType;
        private String screeningStatus;
        private BigDecimal matchScore;
        private Integer matchCount = 0;
        private Boolean isPep = false;
        private Boolean isSanctioned = false;
        private Map<String, Object> matchDetails;
        private String screeningProvider;
        private LocalDateTime screenedAt = LocalDateTime.now();
        private String screenedBy;
        private String notes;

        OwnerScreeningResultBuilder() {
        }

        public OwnerScreeningResultBuilder screeningId(Long screeningId) {
            this.screeningId = screeningId;
            return this;
        }

        public OwnerScreeningResultBuilder owner(BeneficialOwner owner) {
            this.owner = owner;
            return this;
        }

        public OwnerScreeningResultBuilder screeningType(String screeningType) {
            this.screeningType = screeningType;
            return this;
        }

        public OwnerScreeningResultBuilder screeningStatus(String screeningStatus) {
            this.screeningStatus = screeningStatus;
            return this;
        }

        public OwnerScreeningResultBuilder matchScore(BigDecimal matchScore) {
            this.matchScore = matchScore;
            return this;
        }

        public OwnerScreeningResultBuilder matchCount(Integer matchCount) {
            this.matchCount = matchCount;
            return this;
        }

        public OwnerScreeningResultBuilder isPep(Boolean isPep) {
            this.isPep = isPep;
            return this;
        }

        public OwnerScreeningResultBuilder isSanctioned(Boolean isSanctioned) {
            this.isSanctioned = isSanctioned;
            return this;
        }

        public OwnerScreeningResultBuilder matchDetails(Map<String, Object> matchDetails) {
            this.matchDetails = matchDetails;
            return this;
        }

        public OwnerScreeningResultBuilder screeningProvider(String screeningProvider) {
            this.screeningProvider = screeningProvider;
            return this;
        }

        public OwnerScreeningResultBuilder screenedAt(LocalDateTime screenedAt) {
            this.screenedAt = screenedAt;
            return this;
        }

        public OwnerScreeningResultBuilder screenedBy(String screenedBy) {
            this.screenedBy = screenedBy;
            return this;
        }

        public OwnerScreeningResultBuilder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public OwnerScreeningResult build() {
            return new OwnerScreeningResult(screeningId, owner, screeningType, screeningStatus, matchScore, matchCount,
                    isPep, isSanctioned, matchDetails, screeningProvider, screenedAt, screenedBy, notes);
        }

        public String toString() {
            return "OwnerScreeningResult.OwnerScreeningResultBuilder(screeningId=" + this.screeningId + ", owner="
                    + this.owner + ", screeningType=" + this.screeningType + ", screeningStatus=" + this.screeningStatus
                    + ", matchScore=" + this.matchScore + ", matchCount=" + this.matchCount + ", isPep=" + this.isPep
                    + ", isSanctioned=" + this.isSanctioned + ", matchDetails=" + this.matchDetails
                    + ", screeningProvider=" + this.screeningProvider + ", screenedAt=" + this.screenedAt
                    + ", screenedBy=" + this.screenedBy + ", notes=" + this.notes + ")";
        }
    }
}
