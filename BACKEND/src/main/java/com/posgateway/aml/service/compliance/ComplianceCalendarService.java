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
import java.util.Set;

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
    private final com.posgateway.aml.service.security.PspIsolationService pspIsolationService;

    @Autowired
    public ComplianceCalendarService(ComplianceDeadlineRepository deadlineRepository,
            SuspiciousActivityReportRepository sarRepository,
            NotificationService notificationService,
            com.posgateway.aml.service.security.PspIsolationService pspIsolationService) {
        this.deadlineRepository = deadlineRepository;
        this.sarRepository = sarRepository;
        this.notificationService = notificationService;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * Get upcoming deadlines for a PSP (plus any platform-wide deadlines with pspId=NULL).
     * Pass null pspId for platform admins to see everything.
     */
    public List<ComplianceDeadline> getUpcomingDeadlines(int daysAhead, Long pspId) {
        LocalDateTime endDate = LocalDateTime.now().plusDays(daysAhead);
        if (pspId == null) {
            return deadlineRepository.findByDeadlineDateBetweenAndCompletedFalse(
                    LocalDateTime.now(), endDate);
        }
        return deadlineRepository.findUpcomingForPsp(pspId, LocalDateTime.now(), endDate);
    }

    /**
     * Get overdue deadlines for a PSP (plus platform-wide).
     */
    public List<ComplianceDeadline> getOverdueDeadlines(Long pspId) {
        if (pspId == null) {
            return deadlineRepository.findByDeadlineDateBeforeAndCompletedFalse(LocalDateTime.now());
        }
        return deadlineRepository.findOverdueForPsp(pspId, LocalDateTime.now());
    }

    // Backwards-compatible overloads (treats caller as platform admin — used by scheduled jobs).
    public List<ComplianceDeadline> getUpcomingDeadlines(int daysAhead) {
        return getUpcomingDeadlines(daysAhead, null);
    }

    public List<ComplianceDeadline> getOverdueDeadlines() {
        return getOverdueDeadlines(null);
    }

    /**
     * Check SAR filing deadlines
     */
    @Scheduled(cron = "${compliance.sar-deadline-check-cron:0 */15 * * * *}")
    @Transactional
    public void checkSarFilingDeadlines() {
        LocalDateTime now = LocalDateTime.now();
        List<com.posgateway.aml.model.SarStatus> openStatuses = List.of(
                com.posgateway.aml.model.SarStatus.DRAFT,
                com.posgateway.aml.model.SarStatus.PENDING_REVIEW,
                com.posgateway.aml.model.SarStatus.APPROVED);
        List<SuspiciousActivityReport> candidates = sarRepository
                .findByStatusInAndFilingDeadlineBefore(openStatuses, now.plusDays(7));

        for (SuspiciousActivityReport sar : candidates) {
            if (sar.getFilingDeadline() == null) continue;
            if (now.isAfter(sar.getFilingDeadline())) {
                sar.setDeadlineBreached(true);
                if (sar.getDeadlineBreachNotifiedAt() == null) {
                    notificationService.sendSystemAlert("compliance-deadlines",
                            "BREACH: " + sar.getSarReference() + " missed its filing deadline "
                                    + sar.getFilingDeadline() + " under policy " + sar.getDeadlinePolicyCode());
                    sar.setDeadlineBreachNotifiedAt(now);
                }
            } else if (sar.getDeadlineWarningAt() != null
                    && !now.isBefore(sar.getDeadlineWarningAt())
                    && sar.getDeadlineWarningSentAt() == null) {
                notificationService.sendSystemAlert("compliance-deadlines",
                        "Due soon: " + sar.getSarReference() + " must be filed by "
                                + sar.getFilingDeadline() + " under policy " + sar.getDeadlinePolicyCode());
                sar.setDeadlineWarningSentAt(now);
            }
            sarRepository.save(sar);
        }
    }

    /**
     * Create compliance deadline scoped to a specific PSP.
     * Pass pspId=null for a platform-wide deadline (visible to every tenant).
     */
    @Transactional
    public ComplianceDeadline createDeadline(String deadlineType, LocalDateTime deadlineDate,
            String description, String jurisdiction, Long pspId) {
        ComplianceDeadline deadline = new ComplianceDeadline();
        deadline.setDeadlineType(deadlineType);
        deadline.setDeadlineDate(deadlineDate);
        deadline.setDescription(description);
        deadline.setJurisdiction(jurisdiction);
        deadline.setPspId(pspId);
        deadline.setCompleted(false);

        return deadlineRepository.save(deadline);
    }

    @Transactional
    public ComplianceDeadline upsertSourceDeadline(String deadlineType, LocalDateTime deadlineDate,
            String description, String jurisdiction, Long pspId, String sourceType, Long sourceId) {
        ComplianceDeadline deadline = deadlineRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .orElseGet(ComplianceDeadline::new);
        deadline.setDeadlineType(deadlineType);
        deadline.setDeadlineDate(deadlineDate);
        deadline.setDescription(description);
        deadline.setJurisdiction(jurisdiction);
        deadline.setPspId(pspId);
        deadline.setSourceType(sourceType);
        deadline.setSourceId(sourceId);
        deadline.setCompleted(false);
        deadline.setCompletedAt(null);
        return deadlineRepository.save(deadline);
    }

    @Transactional
    public void completeSourceDeadline(String sourceType, Long sourceId) {
        deadlineRepository.findBySourceTypeAndSourceId(sourceType, sourceId).ifPresent(deadline -> {
            deadline.setCompleted(true);
            deadline.setCompletedAt(LocalDateTime.now());
            deadlineRepository.save(deadline);
        });
    }

    // Backwards-compatible overload (platform-wide).
    @Transactional
    public ComplianceDeadline createDeadline(String deadlineType, LocalDateTime deadlineDate,
            String description, String jurisdiction) {
        return createDeadline(deadlineType, deadlineDate, description, jurisdiction, null);
    }

    /**
     * Mark deadline as completed
     */
    @Transactional
    public void markDeadlineCompleted(Long deadlineId) {
        ComplianceDeadline deadline = deadlineRepository.findById(deadlineId)
                .orElseThrow(() -> new IllegalArgumentException("Deadline not found"));
        // Tenant isolation: only the deadline's own PSP (or a platform admin) may complete it.
        pspIsolationService.validatePspAccess(deadline.getPspId());
        deadline.setCompleted(true);
        deadline.setCompletedAt(LocalDateTime.now());
        deadlineRepository.save(deadline);
    }
}
