package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.alert.FalsePositiveFeedback;
import com.posgateway.aml.model.AlertDisposition;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.alert.FalsePositiveFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * False Positive Feedback Service
 * Collects and processes false positive feedback to improve rules
 */
@Service
public class FalsePositiveFeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(FalsePositiveFeedbackService.class);

    private final AlertRepository alertRepository;
    private final FalsePositiveFeedbackRepository feedbackRepository;
    private final AlertTuningService alertTuningService;

    @Autowired
    public FalsePositiveFeedbackService(
            AlertRepository alertRepository,
            FalsePositiveFeedbackRepository feedbackRepository,
            AlertTuningService alertTuningService) {
        this.alertRepository = alertRepository;
        this.feedbackRepository = feedbackRepository;
        this.alertTuningService = alertTuningService;
    }

    /**
     * Record false positive feedback
     */
    @Transactional
    public FalsePositiveFeedback recordFeedback(Long alertId, String reason, String ruleName, Long userId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));

        FalsePositiveFeedback feedback = new FalsePositiveFeedback();
        feedback.setAlertId(alertId);
        feedback.setReason(reason);
        feedback.setRuleName(ruleName);
        feedback.setUserId(userId);
        feedback.setCreatedAt(LocalDateTime.now());

        // Update alert disposition if not already set
        if (alert.getDisposition() == null) {
            alert.setDisposition(AlertDisposition.FALSE_POSITIVE);
            alert.setDispositionReason(reason);
            alert.setStatus("closed");
            alertRepository.save(alert);
        }

        feedback = feedbackRepository.save(feedback);
        logger.info("Recorded false positive feedback for alert {}: {}", alertId, reason);

        // Process feedback for rule tuning
        processFeedbackForTuning(feedback);

        return feedback;
    }

    /**
     * Process feedback for rule tuning
     */
    private void processFeedbackForTuning(FalsePositiveFeedback feedback) {
        // Aggregate feedback by rule
        List<FalsePositiveFeedback> ruleFeedback = feedbackRepository.findByRuleName(feedback.getRuleName());

        // If rule has significant false positives, suggest tuning
        if (ruleFeedback.size() >= 5) {
            double falsePositiveRate = calculateFalsePositiveRate(feedback.getRuleName());
            if (falsePositiveRate > 0.3) { // More than 30% false positive rate
                logger.warn("Rule {} has high false positive rate: {}%", 
                        feedback.getRuleName(), falsePositiveRate * 100);
                alertTuningService.suggestTuning(feedback.getRuleName(), falsePositiveRate);
            }
        }
    }

    /**
     * Calculate false positive rate for a rule
     */
    public double calculateFalsePositiveRate(String ruleName) {
        List<FalsePositiveFeedback> falsePositives = feedbackRepository.findByRuleName(ruleName);
        
        // Get total alerts for this rule
        long totalAlerts = alertRepository.count();
        // This is simplified - in reality would need to track alerts by rule
        
        if (totalAlerts == 0) {
            return 0.0;
        }

        return falsePositives.size() / (double) totalAlerts;
    }

    /**
     * Get false positive statistics
     */
    public Map<String, Object> getFalsePositiveStatistics() {
        List<FalsePositiveFeedback> allFeedback = feedbackRepository.findAll();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFalsePositives", allFeedback.size());
        
        // Group by rule
        Map<String, Long> byRule = allFeedback.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getRuleName() != null ? f.getRuleName() : "UNKNOWN",
                        Collectors.counting()));
        stats.put("byRule", byRule);

        // Top false positive rules
        List<Map<String, Object>> topRules = byRule.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(entry -> {
                    Map<String, Object> ruleStat = new HashMap<>();
                    ruleStat.put("ruleName", entry.getKey());
                    ruleStat.put("falsePositiveCount", entry.getValue());
                    ruleStat.put("falsePositiveRate", calculateFalsePositiveRate(entry.getKey()));
                    return ruleStat;
                })
                .collect(Collectors.toList());
        stats.put("topFalsePositiveRules", topRules);

        return stats;
    }
}

