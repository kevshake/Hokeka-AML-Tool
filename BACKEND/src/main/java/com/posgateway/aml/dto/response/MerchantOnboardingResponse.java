package com.posgateway.aml.dto.response;

import com.posgateway.aml.model.ScreeningResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for merchant onboarding
 */
public class MerchantOnboardingResponse {

    private Long merchantId;
    private String legalName;
    private String status; // APPROVED, UNDER_REVIEW, REJECTED, PENDING_SCREENING
    private String decision; // APPROVE, REVIEW, REJECT
    private String decisionReason;
    private String country;

    // Screening Results
    private ScreeningResult merchantScreeningResult;
    private List<OwnerScreeningDetail> beneficialOwnerResults;

    // Risk Assessment
    private Integer riskScore;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private List<String> riskFactors;

    // Compliance Case (if created)
    private Long complianceCaseId;
    private String caseStatus;
    private String casePriority;

    // Metadata
    private LocalDateTime screenedAt;
    private String screeningProvider; // SUMSUB, AEROSPIKE
    private Double screeningCost;
    private String kycStatus;
    private String contractStatus;
    private java.math.BigDecimal dailyLimit;
    private java.math.BigDecimal currentUsage;
    private String mccDescription;
    
    // Additional fields for frontend display
    private String tradingName;
    private String contactEmail;
    private String mcc;
    private String businessType;
    private Double krs;
    private Double cra;

    public MerchantOnboardingResponse() {
    }

    public MerchantOnboardingResponse(Long merchantId, String legalName, String status, String decision,
            String decisionReason, ScreeningResult merchantScreeningResult,
            List<OwnerScreeningDetail> beneficialOwnerResults, Integer riskScore, String riskLevel,
            List<String> riskFactors, Long complianceCaseId, String caseStatus, String casePriority,
            LocalDateTime screenedAt, String screeningProvider, Double screeningCost, String country,
            String kycStatus, String contractStatus, java.math.BigDecimal dailyLimit,
            java.math.BigDecimal currentUsage, String mccDescription, String tradingName,
            String contactEmail, String mcc, String businessType, Double krs, Double cra) {
        this.merchantId = merchantId;
        this.legalName = legalName;
        this.status = status;
        this.decision = decision;
        this.decisionReason = decisionReason;
        this.merchantScreeningResult = merchantScreeningResult;
        this.beneficialOwnerResults = beneficialOwnerResults;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.riskFactors = riskFactors;
        this.complianceCaseId = complianceCaseId;
        this.caseStatus = caseStatus;
        this.casePriority = casePriority;
        this.screenedAt = screenedAt;
        this.screeningProvider = screeningProvider;
        this.screeningCost = screeningCost;
        this.country = country;
        this.kycStatus = kycStatus;
        this.contractStatus = contractStatus;
        this.dailyLimit = dailyLimit;
        this.currentUsage = currentUsage;
        this.mccDescription = mccDescription;
        this.tradingName = tradingName;
        this.contactEmail = contactEmail;
        this.mcc = mcc;
        this.businessType = businessType;
        this.krs = krs;
        this.cra = cra;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public ScreeningResult getMerchantScreeningResult() {
        return merchantScreeningResult;
    }

    public void setMerchantScreeningResult(ScreeningResult merchantScreeningResult) {
        this.merchantScreeningResult = merchantScreeningResult;
    }

    public List<OwnerScreeningDetail> getBeneficialOwnerResults() {
        return beneficialOwnerResults;
    }

    public void setBeneficialOwnerResults(List<OwnerScreeningDetail> beneficialOwnerResults) {
        this.beneficialOwnerResults = beneficialOwnerResults;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<String> getRiskFactors() {
        return riskFactors;
    }

    public void setRiskFactors(List<String> riskFactors) {
        this.riskFactors = riskFactors;
    }

    public Long getComplianceCaseId() {
        return complianceCaseId;
    }

    public void setComplianceCaseId(Long complianceCaseId) {
        this.complianceCaseId = complianceCaseId;
    }

    public String getCaseStatus() {
        return caseStatus;
    }

    public void setCaseStatus(String caseStatus) {
        this.caseStatus = caseStatus;
    }

    public String getCasePriority() {
        return casePriority;
    }

    public void setCasePriority(String casePriority) {
        this.casePriority = casePriority;
    }

    public LocalDateTime getScreenedAt() {
        return screenedAt;
    }

    public void setScreenedAt(LocalDateTime screenedAt) {
        this.screenedAt = screenedAt;
    }

    public String getScreeningProvider() {
        return screeningProvider;
    }

    public void setScreeningProvider(String screeningProvider) {
        this.screeningProvider = screeningProvider;
    }

    public Double getScreeningCost() {
        return screeningCost;
    }

    public void setScreeningCost(Double screeningCost) {
        this.screeningCost = screeningCost;
    }

    public String getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(String kycStatus) {
        this.kycStatus = kycStatus;
    }

    public String getContractStatus() {
        return contractStatus;
    }

    public void setContractStatus(String contractStatus) {
        this.contractStatus = contractStatus;
    }

    public java.math.BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(java.math.BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public java.math.BigDecimal getCurrentUsage() {
        return currentUsage;
    }

    public void setCurrentUsage(java.math.BigDecimal currentUsage) {
        this.currentUsage = currentUsage;
    }

    public String getMccDescription() {
        return mccDescription;
    }

    public void setMccDescription(String mccDescription) {
        this.mccDescription = mccDescription;
    }

    public String getTradingName() {
        return tradingName;
    }

    public void setTradingName(String tradingName) {
        this.tradingName = tradingName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(String mcc) {
        this.mcc = mcc;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public Double getKrs() {
        return krs;
    }

    public void setKrs(Double krs) {
        this.krs = krs;
    }

    public Double getCra() {
        return cra;
    }

    public void setCra(Double cra) {
        this.cra = cra;
    }

    public static MerchantOnboardingResponseBuilder builder() {
        return new MerchantOnboardingResponseBuilder();
    }

    public static class MerchantOnboardingResponseBuilder {
        private Long merchantId;
        private String legalName;
        private String status;
        private String decision;
        private String decisionReason;
        private ScreeningResult merchantScreeningResult;
        private List<OwnerScreeningDetail> beneficialOwnerResults;
        private Integer riskScore;
        private String riskLevel;
        private List<String> riskFactors;
        private Long complianceCaseId;
        private String caseStatus;
        private String casePriority;
        private LocalDateTime screenedAt;
        private String screeningProvider;
        private Double screeningCost;
        private String country;
        private String kycStatus;
        private String contractStatus;
        private java.math.BigDecimal dailyLimit;
        private java.math.BigDecimal currentUsage;
        private String mccDescription;
        private String tradingName;
        private String contactEmail;
        private String mcc;
        private String businessType;
        private Double krs;
        private Double cra;

        MerchantOnboardingResponseBuilder() {
        }

        public MerchantOnboardingResponseBuilder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public MerchantOnboardingResponseBuilder legalName(String legalName) {
            this.legalName = legalName;
            return this;
        }

        public MerchantOnboardingResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public MerchantOnboardingResponseBuilder decision(String decision) {
            this.decision = decision;
            return this;
        }

        public MerchantOnboardingResponseBuilder decisionReason(String decisionReason) {
            this.decisionReason = decisionReason;
            return this;
        }

        public MerchantOnboardingResponseBuilder merchantScreeningResult(ScreeningResult merchantScreeningResult) {
            this.merchantScreeningResult = merchantScreeningResult;
            return this;
        }

        public MerchantOnboardingResponseBuilder beneficialOwnerResults(
                List<OwnerScreeningDetail> beneficialOwnerResults) {
            this.beneficialOwnerResults = beneficialOwnerResults;
            return this;
        }

        public MerchantOnboardingResponseBuilder riskScore(Integer riskScore) {
            this.riskScore = riskScore;
            return this;
        }

        public MerchantOnboardingResponseBuilder riskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public MerchantOnboardingResponseBuilder riskFactors(List<String> riskFactors) {
            this.riskFactors = riskFactors;
            return this;
        }

        public MerchantOnboardingResponseBuilder complianceCaseId(Long complianceCaseId) {
            this.complianceCaseId = complianceCaseId;
            return this;
        }

        public MerchantOnboardingResponseBuilder caseStatus(String caseStatus) {
            this.caseStatus = caseStatus;
            return this;
        }

        public MerchantOnboardingResponseBuilder casePriority(String casePriority) {
            this.casePriority = casePriority;
            return this;
        }

        public MerchantOnboardingResponseBuilder screenedAt(LocalDateTime screenedAt) {
            this.screenedAt = screenedAt;
            return this;
        }

        public MerchantOnboardingResponseBuilder screeningProvider(String screeningProvider) {
            this.screeningProvider = screeningProvider;
            return this;
        }

        public MerchantOnboardingResponseBuilder screeningCost(Double screeningCost) {
            this.screeningCost = screeningCost;
            return this;
        }

        public MerchantOnboardingResponseBuilder country(String country) {
            this.country = country;
            return this;
        }

        public MerchantOnboardingResponseBuilder kycStatus(String kycStatus) {
            this.kycStatus = kycStatus;
            return this;
        }

        public MerchantOnboardingResponseBuilder contractStatus(String contractStatus) {
            this.contractStatus = contractStatus;
            return this;
        }

        public MerchantOnboardingResponseBuilder dailyLimit(java.math.BigDecimal dailyLimit) {
            this.dailyLimit = dailyLimit;
            return this;
        }

        public MerchantOnboardingResponseBuilder currentUsage(java.math.BigDecimal currentUsage) {
            this.currentUsage = currentUsage;
            return this;
        }

        public MerchantOnboardingResponseBuilder mccDescription(String mccDescription) {
            this.mccDescription = mccDescription;
            return this;
        }

        public MerchantOnboardingResponseBuilder tradingName(String tradingName) {
            this.tradingName = tradingName;
            return this;
        }

        public MerchantOnboardingResponseBuilder contactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
            return this;
        }

        public MerchantOnboardingResponseBuilder mcc(String mcc) {
            this.mcc = mcc;
            return this;
        }

        public MerchantOnboardingResponseBuilder businessType(String businessType) {
            this.businessType = businessType;
            return this;
        }

        public MerchantOnboardingResponseBuilder krs(Double krs) {
            this.krs = krs;
            return this;
        }

        public MerchantOnboardingResponseBuilder cra(Double cra) {
            this.cra = cra;
            return this;
        }

        public MerchantOnboardingResponse build() {
            return new MerchantOnboardingResponse(merchantId, legalName, status, decision, decisionReason,
                    merchantScreeningResult, beneficialOwnerResults, riskScore, riskLevel, riskFactors,
                    complianceCaseId, caseStatus, casePriority, screenedAt, screeningProvider, screeningCost, country,
                    kycStatus, contractStatus, dailyLimit, currentUsage, mccDescription, tradingName,
                    contactEmail, mcc, businessType, krs, cra);
        }

        public String toString() {
            return "MerchantOnboardingResponse.MerchantOnboardingResponseBuilder(merchantId=" + this.merchantId
                    + ", legalName=" + this.legalName + ", status=" + this.status + ", decision=" + this.decision
                    + ", decisionReason=" + this.decisionReason + ", merchantScreeningResult="
                    + this.merchantScreeningResult + ", beneficialOwnerResults=" + this.beneficialOwnerResults
                    + ", riskScore=" + this.riskScore + ", riskLevel=" + this.riskLevel + ", riskFactors="
                    + this.riskFactors + ", complianceCaseId=" + this.complianceCaseId + ", caseStatus="
                    + this.caseStatus + ", casePriority=" + this.casePriority + ", screenedAt=" + this.screenedAt
                    + ", screeningProvider=" + this.screeningProvider + ", screeningCost=" + this.screeningCost + ")";
        }
    }

    public static class OwnerScreeningDetail {
        private Long ownerId;
        private String fullName;
        private ScreeningResult screeningResult;
        private Boolean isSanctioned;
        private Boolean isPep;

        public OwnerScreeningDetail() {
        }

        public OwnerScreeningDetail(Long ownerId, String fullName, ScreeningResult screeningResult,
                Boolean isSanctioned, Boolean isPep) {
            this.ownerId = ownerId;
            this.fullName = fullName;
            this.screeningResult = screeningResult;
            this.isSanctioned = isSanctioned;
            this.isPep = isPep;
        }

        public Long getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(Long ownerId) {
            this.ownerId = ownerId;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public ScreeningResult getScreeningResult() {
            return screeningResult;
        }

        public void setScreeningResult(ScreeningResult screeningResult) {
            this.screeningResult = screeningResult;
        }

        public Boolean getIsSanctioned() {
            return isSanctioned;
        }

        public void setIsSanctioned(Boolean isSanctioned) {
            this.isSanctioned = isSanctioned;
        }

        public Boolean getIsPep() {
            return isPep;
        }

        public void setIsPep(Boolean isPep) {
            this.isPep = isPep;
        }

        public static OwnerScreeningDetailBuilder builder() {
            return new OwnerScreeningDetailBuilder();
        }

        public static class OwnerScreeningDetailBuilder {
            private Long ownerId;
            private String fullName;
            private ScreeningResult screeningResult;
            private Boolean isSanctioned;
            private Boolean isPep;

            OwnerScreeningDetailBuilder() {
            }

            public OwnerScreeningDetailBuilder ownerId(Long ownerId) {
                this.ownerId = ownerId;
                return this;
            }

            public OwnerScreeningDetailBuilder fullName(String fullName) {
                this.fullName = fullName;
                return this;
            }

            public OwnerScreeningDetailBuilder screeningResult(ScreeningResult screeningResult) {
                this.screeningResult = screeningResult;
                return this;
            }

            public OwnerScreeningDetailBuilder isSanctioned(Boolean isSanctioned) {
                this.isSanctioned = isSanctioned;
                return this;
            }

            public OwnerScreeningDetailBuilder isPep(Boolean isPep) {
                this.isPep = isPep;
                return this;
            }

            public OwnerScreeningDetail build() {
                return new OwnerScreeningDetail(ownerId, fullName, screeningResult, isSanctioned, isPep);
            }

            public String toString() {
                return "MerchantOnboardingResponse.OwnerScreeningDetail.OwnerScreeningDetailBuilder(ownerId="
                        + this.ownerId + ", fullName=" + this.fullName + ", screeningResult=" + this.screeningResult
                        + ", isSanctioned=" + this.isSanctioned + ", isPep=" + this.isPep + ")";
            }
        }
    }
}
