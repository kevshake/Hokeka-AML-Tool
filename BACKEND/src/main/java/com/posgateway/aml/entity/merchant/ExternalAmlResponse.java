package com.posgateway.aml.entity.merchant;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * External AML provider response entity (Sumsub, etc.)
 * Stores raw API responses for audit trail
 */
@Entity
@Table(name = "external_aml_responses")
public class ExternalAmlResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "response_id")
    private Long responseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private BeneficialOwner owner;

    // Provider Details
    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName; // SUMSUB, COMPLYADVANTAGE

    @Column(name = "screening_type", nullable = false, length = 50)
    private String screeningType; // MERCHANT, BENEFICIAL_OWNER

    // Request/Response (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> responsePayload;

    @Column(name = "response_status", length = 50)
    private String responseStatus; // SUCCESS, ERROR, TIMEOUT

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    // Results Summary
    @Column(name = "sanctions_match")
    private Boolean sanctionsMatch = false;

    @Column(name = "pep_match")
    private Boolean pepMatch = false;

    @Column(name = "adverse_media_match")
    private Boolean adverseMediaMatch = false;

    @Column(name = "overall_risk_level", length = 50)
    private String overallRiskLevel;

    // Billing
    @Column(name = "cost_amount", precision = 10, scale = 4)
    private BigDecimal costAmount;

    @Column(name = "cost_currency", length = 3)
    private String costCurrency = "USD";

    // Metadata
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "screened_by", length = 100)
    private String screenedBy;

    public ExternalAmlResponse() {
    }

    public ExternalAmlResponse(Long responseId, Merchant merchant, BeneficialOwner owner, String providerName,
            String screeningType, Map<String, Object> requestPayload, Map<String, Object> responsePayload,
            String responseStatus, Integer httpStatusCode, Boolean sanctionsMatch, Boolean pepMatch,
            Boolean adverseMediaMatch, String overallRiskLevel, BigDecimal costAmount, String costCurrency,
            LocalDateTime createdAt, String screenedBy) {
        this.responseId = responseId;
        this.merchant = merchant;
        this.owner = owner;
        this.providerName = providerName;
        this.screeningType = screeningType;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
        this.responseStatus = responseStatus;
        this.httpStatusCode = httpStatusCode;
        this.sanctionsMatch = sanctionsMatch != null ? sanctionsMatch : false;
        this.pepMatch = pepMatch != null ? pepMatch : false;
        this.adverseMediaMatch = adverseMediaMatch != null ? adverseMediaMatch : false;
        this.overallRiskLevel = overallRiskLevel;
        this.costAmount = costAmount;
        this.costCurrency = costCurrency != null ? costCurrency : "USD";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.screenedBy = screenedBy;
    }

    public Long getResponseId() {
        return responseId;
    }

    public void setResponseId(Long responseId) {
        this.responseId = responseId;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public BeneficialOwner getOwner() {
        return owner;
    }

    public void setOwner(BeneficialOwner owner) {
        this.owner = owner;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getScreeningType() {
        return screeningType;
    }

    public void setScreeningType(String screeningType) {
        this.screeningType = screeningType;
    }

    public Map<String, Object> getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(Map<String, Object> requestPayload) {
        this.requestPayload = requestPayload;
    }

    public Map<String, Object> getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(Map<String, Object> responsePayload) {
        this.responsePayload = responsePayload;
    }

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public Boolean getSanctionsMatch() {
        return sanctionsMatch;
    }

    public void setSanctionsMatch(Boolean sanctionsMatch) {
        this.sanctionsMatch = sanctionsMatch;
    }

    public Boolean getPepMatch() {
        return pepMatch;
    }

    public void setPepMatch(Boolean pepMatch) {
        this.pepMatch = pepMatch;
    }

    public Boolean getAdverseMediaMatch() {
        return adverseMediaMatch;
    }

    public void setAdverseMediaMatch(Boolean adverseMediaMatch) {
        this.adverseMediaMatch = adverseMediaMatch;
    }

    public String getOverallRiskLevel() {
        return overallRiskLevel;
    }

    public void setOverallRiskLevel(String overallRiskLevel) {
        this.overallRiskLevel = overallRiskLevel;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }

    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    public String getCostCurrency() {
        return costCurrency;
    }

    public void setCostCurrency(String costCurrency) {
        this.costCurrency = costCurrency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getScreenedBy() {
        return screenedBy;
    }

    public void setScreenedBy(String screenedBy) {
        this.screenedBy = screenedBy;
    }

    public static ExternalAmlResponseBuilder builder() {
        return new ExternalAmlResponseBuilder();
    }

    public static class ExternalAmlResponseBuilder {
        private Long responseId;
        private Merchant merchant;
        private BeneficialOwner owner;
        private String providerName;
        private String screeningType;
        private Map<String, Object> requestPayload;
        private Map<String, Object> responsePayload;
        private String responseStatus;
        private Integer httpStatusCode;
        private Boolean sanctionsMatch = false;
        private Boolean pepMatch = false;
        private Boolean adverseMediaMatch = false;
        private String overallRiskLevel;
        private BigDecimal costAmount;
        private String costCurrency = "USD";
        private LocalDateTime createdAt = LocalDateTime.now();
        private String screenedBy;

        ExternalAmlResponseBuilder() {
        }

        public ExternalAmlResponseBuilder responseId(Long responseId) {
            this.responseId = responseId;
            return this;
        }

        public ExternalAmlResponseBuilder merchant(Merchant merchant) {
            this.merchant = merchant;
            return this;
        }

        public ExternalAmlResponseBuilder owner(BeneficialOwner owner) {
            this.owner = owner;
            return this;
        }

        public ExternalAmlResponseBuilder providerName(String providerName) {
            this.providerName = providerName;
            return this;
        }

        public ExternalAmlResponseBuilder screeningType(String screeningType) {
            this.screeningType = screeningType;
            return this;
        }

        public ExternalAmlResponseBuilder requestPayload(Map<String, Object> requestPayload) {
            this.requestPayload = requestPayload;
            return this;
        }

        public ExternalAmlResponseBuilder responsePayload(Map<String, Object> responsePayload) {
            this.responsePayload = responsePayload;
            return this;
        }

        public ExternalAmlResponseBuilder responseStatus(String responseStatus) {
            this.responseStatus = responseStatus;
            return this;
        }

        public ExternalAmlResponseBuilder httpStatusCode(Integer httpStatusCode) {
            this.httpStatusCode = httpStatusCode;
            return this;
        }

        public ExternalAmlResponseBuilder sanctionsMatch(Boolean sanctionsMatch) {
            this.sanctionsMatch = sanctionsMatch;
            return this;
        }

        public ExternalAmlResponseBuilder pepMatch(Boolean pepMatch) {
            this.pepMatch = pepMatch;
            return this;
        }

        public ExternalAmlResponseBuilder adverseMediaMatch(Boolean adverseMediaMatch) {
            this.adverseMediaMatch = adverseMediaMatch;
            return this;
        }

        public ExternalAmlResponseBuilder overallRiskLevel(String overallRiskLevel) {
            this.overallRiskLevel = overallRiskLevel;
            return this;
        }

        public ExternalAmlResponseBuilder costAmount(BigDecimal costAmount) {
            this.costAmount = costAmount;
            return this;
        }

        public ExternalAmlResponseBuilder costCurrency(String costCurrency) {
            this.costCurrency = costCurrency;
            return this;
        }

        public ExternalAmlResponseBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ExternalAmlResponseBuilder screenedBy(String screenedBy) {
            this.screenedBy = screenedBy;
            return this;
        }

        public ExternalAmlResponse build() {
            return new ExternalAmlResponse(responseId, merchant, owner, providerName, screeningType, requestPayload,
                    responsePayload, responseStatus, httpStatusCode, sanctionsMatch, pepMatch, adverseMediaMatch,
                    overallRiskLevel, costAmount, costCurrency, createdAt, screenedBy);
        }

        public String toString() {
            return "ExternalAmlResponse.ExternalAmlResponseBuilder(responseId=" + this.responseId + ", merchant="
                    + this.merchant + ", owner=" + this.owner + ", providerName=" + this.providerName
                    + ", screeningType=" + this.screeningType + ", requestPayload=" + this.requestPayload
                    + ", responsePayload=" + this.responsePayload + ", responseStatus=" + this.responseStatus
                    + ", httpStatusCode=" + this.httpStatusCode + ", sanctionsMatch=" + this.sanctionsMatch
                    + ", pepMatch=" + this.pepMatch + ", adverseMediaMatch=" + this.adverseMediaMatch
                    + ", overallRiskLevel=" + this.overallRiskLevel + ", costAmount=" + this.costAmount
                    + ", costCurrency=" + this.costCurrency + ", createdAt=" + this.createdAt + ", screenedBy="
                    + this.screenedBy + ")";
        }
    }
}
