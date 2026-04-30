package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.alert.AlertTuningRecommendation;
import com.posgateway.aml.repository.alert.AlertTuningRecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Alert Tuning Service
 * Provides ML-based tuning recommendations and tracks rule effectiveness
 */
@Service
public class AlertTuningService {

    private static final Logger logger = LoggerFactory.getLogger(AlertTuningService.class);

    private final AlertTuningRecommendationRepository recommendationRepository;
    private final RuleEffectivenessService ruleEffectivenessService;

    @Autowired
    public AlertTuningService(
            AlertTuningRecommendationRepository recommendationRepository,
            RuleEffectivenessService ruleEffectivenessService) {
        this.recommendationRepository = recommendationRepository;
        this.ruleEffectivenessService = ruleEffectivenessService;
    }

    /**
     * Suggest tuning for a rule based on false positive rate
     */
    @Transactional
    public AlertTuningRecommendation suggestTuning(String ruleName, double falsePositiveRate) {
        // Analyze rule effectiveness
        Map<String, Object> effectiveness = ruleEffectivenessService.getRuleEffectiveness(ruleName);

        // Generate recommendation
        String recommendation = generateRecommendation(ruleName, falsePositiveRate, effectiveness);
        String priority = determinePriority(falsePositiveRate);

        AlertTuningRecommendation tuning = new AlertTuningRecommendation();
        tuning.setRuleName(ruleName);
        tuning.setFalsePositiveRate(falsePositiveRate);
        tuning.setRecommendation(recommendation);
        tuning.setPriority(priority);
        tuning.setStatus("PENDING");
        tuning.setCreatedAt(LocalDateTime.now());

        logger.info("Generated tuning recommendation for rule {}: {}", ruleName, recommendation);
        return recommendationRepository.save(tuning);
    }

    /**
     * Generate tuning recommendation
     */
    private String generateRecommendation(String ruleName, double falsePositiveRate, Map<String, Object> effectiveness) {
        if (falsePositiveRate > 0.5) {
            return "Consider increasing threshold or adding additional conditions to reduce false positives";
        } else if (falsePositiveRate > 0.3) {
            return "Review rule conditions and consider fine-tuning parameters";
        } else {
            return "Rule performance is acceptable, minor adjustments may improve precision";
        }
    }

    /**
     * Determine recommendation priority
     */
    private String determinePriority(double falsePositiveRate) {
        if (falsePositiveRate > 0.5) {
            return "HIGH";
        } else if (falsePositiveRate > 0.3) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * Get pending tuning recommendations
     */
    public List<AlertTuningRecommendation> getPendingRecommendations() {
        return recommendationRepository.findByStatus("PENDING");
    }

    /**
     * Apply tuning recommendation
     */
    @Transactional
    public void applyRecommendation(Long recommendationId, Long appliedBy) {
        AlertTuningRecommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new RuntimeException("Recommendation not found: " + recommendationId));

        recommendation.setStatus("APPLIED");
        recommendation.setAppliedBy(appliedBy);
        recommendation.setAppliedAt(LocalDateTime.now());
        recommendationRepository.save(recommendation);

        logger.info("Applied tuning recommendation {} for rule {}", recommendationId, recommendation.getRuleName());
    }
}

