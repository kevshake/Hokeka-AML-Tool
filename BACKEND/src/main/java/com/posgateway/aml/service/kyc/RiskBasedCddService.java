package com.posgateway.aml.service.kyc;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.service.risk.CustomerRiskProfilingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Risk-Based CDD Service
 * Implements risk-based Customer Due Diligence
 */
@Service
public class RiskBasedCddService {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(RiskBasedCddService.class);

    private final CustomerRiskProfilingService riskProfilingService;

    @Autowired
    public RiskBasedCddService(CustomerRiskProfilingService riskProfilingService) {
        this.riskProfilingService = riskProfilingService;
    }

    /**
     * Assess customer risk at onboarding
     */
    public CddAssessment assessCustomerRisk(Merchant merchant) {
        String merchantId = merchant.getMerchantId() != null ? 
                merchant.getMerchantId().toString() : null;

        if (merchantId == null) {
            return CddAssessment.builder()
                    .riskLevel("MEDIUM")
                    .cddLevel("STANDARD")
                    .requiredDocuments(List.of("IDENTITY", "ADDRESS"))
                    .build();
        }

        CustomerRiskProfilingService.CustomerRiskRating rating = 
                riskProfilingService.calculateRiskRating(merchantId);

        String cddLevel;
        List<String> requiredDocuments;

        if (rating.getRiskLevel().equals("HIGH") || riskProfilingService.isEddRequired(merchantId)) {
            cddLevel = "ENHANCED";
            requiredDocuments = List.of("IDENTITY", "ADDRESS", "BENEFICIAL_OWNERSHIP", 
                    "SOURCE_OF_FUNDS", "BUSINESS_REGISTRATION");
        } else if (rating.getRiskLevel().equals("LOW")) {
            cddLevel = "SIMPLIFIED";
            requiredDocuments = List.of("IDENTITY");
        } else {
            cddLevel = "STANDARD";
            requiredDocuments = List.of("IDENTITY", "ADDRESS");
        }

        return CddAssessment.builder()
                .riskLevel(rating.getRiskLevel())
                .riskScore(rating.getRiskScore())
                .cddLevel(cddLevel)
                .requiredDocuments(requiredDocuments)
                .eddRequired(riskProfilingService.isEddRequired(merchantId))
                .build();
    }

    /**
     * CDD Assessment DTO
     */
    public static class CddAssessment {
        private String riskLevel;
        private double riskScore;
        private String cddLevel; // SIMPLIFIED, STANDARD, ENHANCED
        private List<String> requiredDocuments;
        private boolean eddRequired;

        public static CddAssessmentBuilder builder() {
            return new CddAssessmentBuilder();
        }

        // Getters and Setters
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        public String getCddLevel() { return cddLevel; }
        public void setCddLevel(String cddLevel) { this.cddLevel = cddLevel; }
        public List<String> getRequiredDocuments() { return requiredDocuments; }
        public void setRequiredDocuments(List<String> requiredDocuments) { 
            this.requiredDocuments = requiredDocuments; 
        }
        public boolean isEddRequired() { return eddRequired; }
        public void setEddRequired(boolean eddRequired) { this.eddRequired = eddRequired; }

        public static class CddAssessmentBuilder {
            private String riskLevel;
            private double riskScore;
            private String cddLevel;
            private List<String> requiredDocuments;
            private boolean eddRequired;

            public CddAssessmentBuilder riskLevel(String riskLevel) {
                this.riskLevel = riskLevel;
                return this;
            }

            public CddAssessmentBuilder riskScore(double riskScore) {
                this.riskScore = riskScore;
                return this;
            }

            public CddAssessmentBuilder cddLevel(String cddLevel) {
                this.cddLevel = cddLevel;
                return this;
            }

            public CddAssessmentBuilder requiredDocuments(List<String> requiredDocuments) {
                this.requiredDocuments = requiredDocuments;
                return this;
            }

            public CddAssessmentBuilder eddRequired(boolean eddRequired) {
                this.eddRequired = eddRequired;
                return this;
            }

            public CddAssessment build() {
                CddAssessment assessment = new CddAssessment();
                assessment.riskLevel = this.riskLevel;
                assessment.riskScore = this.riskScore;
                assessment.cddLevel = this.cddLevel;
                assessment.requiredDocuments = this.requiredDocuments;
                assessment.eddRequired = this.eddRequired;
                return assessment;
            }
        }
    }
}

