package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.compliance.AuditTrail;
import com.posgateway.aml.repository.AuditTrailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Audit Report Generation Service
 * Generates regulatory audit reports
 */
@Service
public class AuditReportService {

    private static final Logger logger = LoggerFactory.getLogger(AuditReportService.class);

    private final AuditTrailRepository auditTrailRepository;

    public AuditReportService(AuditTrailRepository auditTrailRepository) {
        this.auditTrailRepository = auditTrailRepository;
    }

    /**
     * Generate comprehensive audit report
     */
    public AuditReport generateAuditReport(LocalDateTime startDate, LocalDateTime endDate) {
        List<AuditTrail> auditLogs = auditTrailRepository.findByPerformedAtBetween(startDate, endDate);

        AuditReport report = new AuditReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setTotalEvents(auditLogs.size());
        report.setGeneratedAt(LocalDateTime.now());

        // Group by action type
        Map<String, Long> byActionType = new HashMap<>();
        Map<String, Long> byUser = new HashMap<>();
        Map<String, Long> byEntityType = new HashMap<>();

        for (AuditTrail log : auditLogs) {
            byActionType.merge(log.getAction() != null ? log.getAction() : "UNKNOWN", 1L, (oldValue, newValue) -> oldValue + newValue);
            byUser.merge(log.getPerformedBy() != null ? log.getPerformedBy() : "UNKNOWN", 1L, (oldValue, newValue) -> oldValue + newValue);
            // Entity type would be derived from merchantId or other fields
            String entityType = log.getMerchantId() != null ? "MERCHANT" : "OTHER";
            byEntityType.merge(entityType, 1L, (oldValue, newValue) -> oldValue + newValue);
        }

        report.setEventsByActionType(byActionType);
        report.setEventsByUser(byUser);
        report.setEventsByEntityType(byEntityType);

        // Calculate statistics
        report.setUniqueUsers(byUser.size());
        report.setUniqueEntityTypes(byEntityType.size());

        logger.info("Generated audit report: {} events from {} to {}", 
                auditLogs.size(), startDate, endDate);

        return report;
    }

    /**
     * Generate user activity report
     */
    public UserActivityReport generateUserActivityReport(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        // Get all logs in date range and filter by user
        List<AuditTrail> allLogs = auditTrailRepository.findByPerformedAtBetween(startDate, endDate);
        List<AuditTrail> userLogs = allLogs.stream()
                .filter(log -> userId.equals(log.getPerformedBy()))
                .toList();

        UserActivityReport report = new UserActivityReport();
        report.setUserId(userId);
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setTotalActions(userLogs.size());

        // Group by action
        Map<String, Long> byAction = new HashMap<>();
        for (AuditTrail log : userLogs) {
            // Use generic action field from AuditTrail
            String action = log.getAction() != null ? log.getAction() : "UNKNOWN";
            byAction.merge(action, 1L, (oldValue, newValue) -> oldValue + newValue);
        }
        report.setActionsByType(byAction);

        return report;
    }

    /**
     * Audit Report DTO
     */
    public static class AuditReport {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private LocalDateTime generatedAt;
        private long totalEvents;
        private int uniqueUsers;
        private int uniqueEntityTypes;
        private Map<String, Long> eventsByActionType;
        private Map<String, Long> eventsByUser;
        private Map<String, Long> eventsByEntityType;

        // Getters and Setters
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
        public long getTotalEvents() { return totalEvents; }
        public void setTotalEvents(long totalEvents) { this.totalEvents = totalEvents; }
        public int getUniqueUsers() { return uniqueUsers; }
        public void setUniqueUsers(int uniqueUsers) { this.uniqueUsers = uniqueUsers; }
        public int getUniqueEntityTypes() { return uniqueEntityTypes; }
        public void setUniqueEntityTypes(int uniqueEntityTypes) { this.uniqueEntityTypes = uniqueEntityTypes; }
        public Map<String, Long> getEventsByActionType() { return eventsByActionType; }
        public void setEventsByActionType(Map<String, Long> eventsByActionType) { this.eventsByActionType = eventsByActionType; }
        public Map<String, Long> getEventsByUser() { return eventsByUser; }
        public void setEventsByUser(Map<String, Long> eventsByUser) { this.eventsByUser = eventsByUser; }
        public Map<String, Long> getEventsByEntityType() { return eventsByEntityType; }
        public void setEventsByEntityType(Map<String, Long> eventsByEntityType) { this.eventsByEntityType = eventsByEntityType; }
    }

    /**
     * User Activity Report DTO
     */
    public static class UserActivityReport {
        private String userId;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private long totalActions;
        private Map<String, Long> actionsByType;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        public long getTotalActions() { return totalActions; }
        public void setTotalActions(long totalActions) { this.totalActions = totalActions; }
        public Map<String, Long> getActionsByType() { return actionsByType; }
        public void setActionsByType(Map<String, Long> actionsByType) { this.actionsByType = actionsByType; }
    }
}

