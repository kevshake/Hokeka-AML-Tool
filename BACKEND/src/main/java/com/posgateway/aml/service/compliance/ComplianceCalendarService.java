package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.repository.ComplianceDeadlineRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Compliance Calendar Service
 * Manages regulatory filing deadlines and compliance calendar
 */
@Service
public class ComplianceCalendarService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceCalendarService.class);

    private final ComplianceDeadlineRepository deadlineRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    private final NotificationService notificationService;

    @Autowired
    public ComplianceCalendarService(ComplianceDeadlineRepository deadlineRepository,
            SuspiciousActivityReportRepository sarRepository,
            NotificationService notificationService) {
        this.deadlineRepository = deadlineRepository;
        this.sarRepository = sarRepository;
        this.notificationService = notificationService;
    }

    /**
     * Get upcoming deadlines
     */
    public List<ComplianceDeadline> getUpcomingDeadlines(int daysAhead) {
        LocalDateTime endDate = LocalDateTime.now().plusDays(daysAhead);
        return deadlineRepository.findByDeadlineDateBetweenAndCompletedFalse(
                LocalDateTime.now(), endDate);
    }

    /**
     * Get overdue deadlines
     */
    public List<ComplianceDeadline> getOverdueDeadlines() {
        return deadlineRepository.findByDeadlineDateBeforeAndCompletedFalse(LocalDateTime.now());
    }

    /**
     * Check SAR filing deadlines
     */
    @Scheduled(cron = "0 0 9 * * *") // Daily at 9 AM
    @Transactional
    public void checkSarFilingDeadlines() {
        LocalDateTime warningDate = LocalDateTime.now().plusDays(3);
        List<SuspiciousActivityReport> sarsDueSoon = sarRepository
                .findByFilingDeadlineBeforeAndStatusNot(warningDate,
                        com.posgateway.aml.model.SarStatus.FILED);

        if (!sarsDueSoon.isEmpty()) {
            logger.warn("Found {} SARs with upcoming filing deadlines", sarsDueSoon.size());
            // Send notifications
            String message = String.format("Alert: %d SARs are due for filing within 7 days.", sarsDueSoon.size());
            notificationService.sendSystemAlert("compliance-deadlines", message);
        }
    }

    /**
     * Create compliance deadline
     */
    @Transactional
    public ComplianceDeadline createDeadline(String deadlineType, LocalDateTime deadlineDate,
            String description, String jurisdiction) {
        ComplianceDeadline deadline = new ComplianceDeadline();
        deadline.setDeadlineType(deadlineType);
        deadline.setDeadlineDate(deadlineDate);
        deadline.setDescription(description);
        deadline.setJurisdiction(jurisdiction);
        deadline.setCompleted(false);

        return deadlineRepository.save(deadline);
    }

    /**
     * Mark deadline as completed
     */
    @Transactional
    public void markDeadlineCompleted(Long deadlineId) {
        ComplianceDeadline deadline = deadlineRepository.findById(deadlineId)
                .orElseThrow(() -> new IllegalArgumentException("Deadline not found"));
        deadline.setCompleted(true);
        deadline.setCompletedAt(LocalDateTime.now());
        deadlineRepository.save(deadline);
    }
}
