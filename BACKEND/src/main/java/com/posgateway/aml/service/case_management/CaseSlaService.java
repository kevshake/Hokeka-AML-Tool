package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Case SLA Service
 * Manages SLA deadlines, case aging, and SLA compliance tracking
 */
@Service
public class CaseSlaService {

    private static final Logger logger = LoggerFactory.getLogger(CaseSlaService.class);

    private final ComplianceCaseRepository caseRepository;
    private final BusinessDayCalculator businessDayCalculator;

    @Value("${case.sla.days.low:7}")
    private int slaDaysLow;

    @Value("${case.sla.days.medium:5}")
    private int slaDaysMedium;

    @Value("${case.sla.days.high:3}")
    private int slaDaysHigh;

    @Value("${case.sla.days.critical:1}")
    private int slaDaysCritical;

    @Autowired
    public CaseSlaService(ComplianceCaseRepository caseRepository,
                          BusinessDayCalculator businessDayCalculator) {
        this.caseRepository = caseRepository;
        this.businessDayCalculator = businessDayCalculator;
    }

    /**
     * Calculate and set SLA deadline for a case based on priority
     */
    @Transactional
    public void calculateSlaDeadline(ComplianceCase complianceCase) {
        int slaDays = getSlaDaysForPriority(complianceCase.getPriority());
        LocalDateTime deadline = businessDayCalculator.addBusinessDays(
                complianceCase.getCreatedAt() != null ? complianceCase.getCreatedAt() : LocalDateTime.now(),
                slaDays
        );
        complianceCase.setSlaDeadline(deadline);
        caseRepository.save(complianceCase);
        logger.debug("Calculated SLA deadline for case {}: {}", complianceCase.getCaseReference(), deadline);
    }

    /**
     * Update case aging (days open)
     */
    @Transactional
    public void updateCaseAging(ComplianceCase complianceCase) {
        if (complianceCase.getCreatedAt() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        long daysOpen = ChronoUnit.DAYS.between(complianceCase.getCreatedAt(), now);
        complianceCase.setDaysOpen((int) daysOpen);
        caseRepository.save(complianceCase);
    }

    /**
     * Check SLA status for a case
     */
    public CaseSlaStatus checkSlaStatus(ComplianceCase complianceCase) {
        if (complianceCase.getSlaDeadline() == null) {
            return CaseSlaStatus.NO_SLA;
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(complianceCase.getSlaDeadline())) {
            return CaseSlaStatus.BREACHED;
        } else if (now.isAfter(complianceCase.getSlaDeadline().minusDays(1))) {
            return CaseSlaStatus.AT_RISK;
        }
        return CaseSlaStatus.ON_TRACK;
    }

    /**
     * Get SLA days for priority
     */
    private int getSlaDaysForPriority(CasePriority priority) {
        return switch (priority) {
            case CRITICAL -> slaDaysCritical;
            case HIGH -> slaDaysHigh;
            case MEDIUM -> slaDaysMedium;
            case LOW -> slaDaysLow;
        };
    }

    /**
     * Scheduled task to update case aging daily
     */
    @Scheduled(cron = "0 0 1 * * *") // Daily at 1 AM
    @Transactional
    public void updateAllCaseAging() {
        logger.info("Starting daily case aging update");
        List<CaseStatus> openStatuses = List.of(
                CaseStatus.NEW,
                CaseStatus.ASSIGNED,
                CaseStatus.IN_PROGRESS,
                CaseStatus.PENDING_REVIEW,
                CaseStatus.ESCALATED,
                CaseStatus.PENDING_INFO
        );

        List<ComplianceCase> openCases = caseRepository.findByStatusIn(openStatuses);
        int updated = 0;
        int breached = 0;
        int atRisk = 0;

        for (ComplianceCase complianceCase : openCases) {
            updateCaseAging(complianceCase);
            CaseSlaStatus slaStatus = checkSlaStatus(complianceCase);
            
            if (slaStatus == CaseSlaStatus.BREACHED) {
                breached++;
                notifySlaBreach(complianceCase, slaStatus);
            } else if (slaStatus == CaseSlaStatus.AT_RISK) {
                atRisk++;
                notifySlaBreach(complianceCase, slaStatus);
            }
            updated++;
        }

        logger.info("Case aging update completed: {} cases updated, {} breached, {} at risk", 
                updated, breached, atRisk);
    }

    /**
     * Notify about SLA breach or risk
     */
    private void notifySlaBreach(ComplianceCase complianceCase, CaseSlaStatus slaStatus) {
        // TODO: Integrate with notification service
        logger.warn("Case {} SLA status: {} (Deadline: {})", 
                complianceCase.getCaseReference(), 
                slaStatus, 
                complianceCase.getSlaDeadline());
    }

    /**
     * Get cases approaching SLA deadline
     */
    public List<ComplianceCase> getCasesApproachingDeadline(int hoursBeforeDeadline) {
        LocalDateTime threshold = LocalDateTime.now().plusHours(hoursBeforeDeadline);
        return caseRepository.findBySlaDeadlineBeforeAndStatusNot(
                threshold,
                CaseStatus.CLOSED_CLEARED
        );
    }

    /**
     * Get cases with breached SLA
     */
    public List<ComplianceCase> getBreachedCases() {
        LocalDateTime now = LocalDateTime.now();
        return caseRepository.findBySlaDeadlineBeforeAndStatusNot(
                now,
                CaseStatus.CLOSED_CLEARED
        );
    }

    /**
     * SLA Status enum
     */
    public enum CaseSlaStatus {
        NO_SLA,
        ON_TRACK,
        AT_RISK,
        BREACHED
    }
}

