package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Alert Prioritization Service
 * Prioritizes alerts based on various factors
 */
@Service
public class AlertPrioritizationService {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(AlertPrioritizationService.class);

    private final AlertRepository alertRepository;

    @Autowired
    public AlertPrioritizationService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Calculate alert priority score
     */
    public double calculatePriorityScore(Alert alert) {
        double score = 0.0;

        // Age factor (older alerts get higher priority)
        if (alert.getCreatedAt() != null) {
            long hoursOld = ChronoUnit.HOURS.between(alert.getCreatedAt(), LocalDateTime.now());
            score += Math.min(hoursOld / 24.0 * 0.3, 0.3); // Max 0.3 from age
        }

        // Risk score factor
        if (alert.getScore() != null) {
            score += alert.getScore() * 0.5; // Up to 0.5 from risk score
        }

        // Severity factor
        if ("CRITICAL".equals(alert.getSeverity())) {
            score += 0.3;
        } else if ("WARN".equals(alert.getSeverity())) {
            score += 0.15;
        }

        return Math.min(score, 1.0);
    }

    /**
     * Prioritize alerts
     */
    public List<Alert> prioritizeAlerts(List<Alert> alerts) {
        return alerts.stream()
                .sorted(Comparator.comparing(this::calculatePriorityScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get prioritized alert queue
     */
    public List<Alert> getPrioritizedQueue() {
        List<Alert> unassignedAlerts = alertRepository.findByAssignedToIsNull();
        return prioritizeAlerts(unassignedAlerts);
    }

    /**
     * Calculate alert age in hours
     */
    public long calculateAlertAge(Alert alert) {
        if (alert.getCreatedAt() != null) {
            return ChronoUnit.HOURS.between(alert.getCreatedAt(), LocalDateTime.now());
        }
        return 0;
    }
}

