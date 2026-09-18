package com.posgateway.aml.entity.merchant;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Merchant screening result entity
 */
@Entity
@Table(name = "merchant_screening_results")
public class MerchantScreeningResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_id")
    private Long screeningId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    // Screening Details
    @Column(name = "screening_type", nullable = false, length = 50)
    private String screeningType; // ONBOARDING, PERIODIC, UPDATE, MANUAL

    @Column(name = "screening_status", nullable = false, length = 50)
    private String screeningStatus; // CLEAR, MATCH, POTENTIAL_MATCH

    @Column(name = "match_score", precision = 5, scale = 4)
    private BigDecimal matchScore;

    @Column(name = "match_count")
    private Integer matchCount = 0;

    // Results (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_details", columnDefinition = "jsonb")
    private Map<String, Object> matchDetails;

    @Column(name = "screening_provider", length = 100)
    private String screeningProvider; // INTERNAL_AEROSPIKE, SUMSUB, etc.

    // Metadata
    @Column(name = "screened_at", nullable = false)
    private LocalDateTime screenedAt = LocalDateTime.now();

    @Column(name = "screened_by", length = 100)
    private String screenedBy;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public MerchantScreeningResult() {
    }

    public MerchantScreeningResult(Long screeningId, Merchant merchant, String screeningType, String screeningStatus,
            BigDecimal matchScore, Integer matchCount, Map<String, Object> matchDetails, String screeningProvider,
            LocalDateTime screenedAt, String screenedBy, String notes) {
        this.screeningId = screeningId;
        this.merchant = merchant;
        this.screeningType = screeningType;
        this.screeningStatus = screeningStatus;
        this.matchScore = matchScore;
        this.matchCount = matchCount != null ? matchCount : 0;
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

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
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

    public static MerchantScreeningResultBuilder builder() {
        return new MerchantScreeningResultBuilder();
    }

    public static class MerchantScreeningResultBuilder {
        private Long screeningId;
        private Merchant merchant;
        private String screeningType;
        private String screeningStatus;
        private BigDecimal matchScore;
        private Integer matchCount = 0;
        private Map<String, Object> matchDetails;
        private String screeningProvider;
        private LocalDateTime screenedAt = LocalDateTime.now();
        private String screenedBy;
        private String notes;

        MerchantScreeningResultBuilder() {
        }

        public MerchantScreeningResultBuilder screeningId(Long screeningId) {
            this.screeningId = screeningId;
            return this;
        }

        public MerchantScreeningResultBuilder merchant(Merchant merchant) {
            this.merchant = merchant;
            return this;
        }

        public MerchantScreeningResultBuilder screeningType(String screeningType) {
            this.screeningType = screeningType;
            return this;
        }

        public MerchantScreeningResultBuilder screeningStatus(String screeningStatus) {
            this.screeningStatus = screeningStatus;
            return this;
        }

        public MerchantScreeningResultBuilder matchScore(BigDecimal matchScore) {
            this.matchScore = matchScore;
            return this;
        }

        public MerchantScreeningResultBuilder matchCount(Integer matchCount) {
            this.matchCount = matchCount;
            return this;
        }

        public MerchantScreeningResultBuilder matchDetails(Map<String, Object> matchDetails) {
            this.matchDetails = matchDetails;
            return this;
        }

        public MerchantScreeningResultBuilder screeningProvider(String screeningProvider) {
            this.screeningProvider = screeningProvider;
            return this;
        }

        public MerchantScreeningResultBuilder screenedAt(LocalDateTime screenedAt) {
            this.screenedAt = screenedAt;
            return this;
        }

        public MerchantScreeningResultBuilder screenedBy(String screenedBy) {
            this.screenedBy = screenedBy;
            return this;
        }

        public MerchantScreeningResultBuilder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public MerchantScreeningResult build() {
            return new MerchantScreeningResult(screeningId, merchant, screeningType, screeningStatus, matchScore,
                    matchCount, matchDetails, screeningProvider, screenedAt, screenedBy, notes);
        }

        public String toString() {
            return "MerchantScreeningResult.MerchantScreeningResultBuilder(screeningId=" + this.screeningId
                    + ", merchant=" + this.merchant + ", screeningType=" + this.screeningType + ", screeningStatus="
                    + this.screeningStatus + ", matchScore=" + this.matchScore + ", matchCount=" + this.matchCount
                    + ", matchDetails=" + this.matchDetails + ", screeningProvider=" + this.screeningProvider
                    + ", screenedAt=" + this.screenedAt + ", screenedBy=" + this.screenedBy + ", notes=" + this.notes
                    + ")";
        }
    }
}
