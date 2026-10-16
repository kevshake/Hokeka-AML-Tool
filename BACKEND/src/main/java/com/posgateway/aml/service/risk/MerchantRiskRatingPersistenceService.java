package com.posgateway.aml.service.risk;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantRiskScore;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.merchant.MerchantRiskScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists behaviour-driven customer risk ratings to merchant_risk_scores and updates merchant CRA/risk_level.
 */
@Service
public class MerchantRiskRatingPersistenceService {

    private final CustomerRiskProfilingService customerRiskProfilingService;
    private final MerchantRepository merchantRepository;
    private final MerchantRiskScoreRepository merchantRiskScoreRepository;

    public MerchantRiskRatingPersistenceService(CustomerRiskProfilingService customerRiskProfilingService,
                                                MerchantRepository merchantRepository,
                                                MerchantRiskScoreRepository merchantRiskScoreRepository) {
        this.customerRiskProfilingService = customerRiskProfilingService;
        this.merchantRepository = merchantRepository;
        this.merchantRiskScoreRepository = merchantRiskScoreRepository;
    }

    /**
     * Recomputes and stores the latest merchant risk profile row, then mirrors CRA on merchants.
     */
    @Transactional
    public MerchantRiskScore refreshMerchantRisk(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
        if (merchant == null) {
            return null;
        }
        CustomerRiskProfilingService.CustomerRiskRating rating =
                customerRiskProfilingService.calculateRiskRating(String.valueOf(merchantId));
        int totalScore = (int) Math.round(rating.getRiskScore() * 100);
        MerchantRiskScore score = MerchantRiskScore.builder()
                .merchant(merchant)
                .totalScore(totalScore)
                .riskLevel(rating.getRiskLevel())
                .decision(rating.getRiskScore() >= 0.7 ? "REVIEW" : "APPROVE")
                .decisionReason("Behaviour-driven refresh from cases and transaction history")
                .rulesVersion("CRA-v1")
                .calculatedBy("risk-refresh-job")
                .build();
        MerchantRiskScore saved = merchantRiskScoreRepository.save(score);
        merchant.setRiskLevel(rating.getRiskLevel());
        merchant.setCra(rating.getRiskScore() * 100);
        merchantRepository.save(merchant);
        return saved;
    }
}
