package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

// @RequiredArgsConstructor removed
@Service
@SuppressWarnings("null") // Repository methods return Optional, saved entities are non-null
public class ComplianceCaseService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ComplianceCaseService.class);

    private final ComplianceCaseRepository caseRepository;

    public ComplianceCaseService(ComplianceCaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    @Transactional
    public ComplianceCase createCase(String description) {
        log.info("Creating compliance case (legacy service) without merchant binding");

        CasePriority priority = CasePriority.MEDIUM;
        LocalDateTime now = LocalDateTime.now();

        ComplianceCase newCase = ComplianceCase.builder()
                .caseReference("CASE-" + UUID.randomUUID())
                .description(description)
                .status(CaseStatus.NEW)
                .priority(priority)
                .slaDeadline(calculateSlaDeadline(priority, now))
                .daysOpen(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return caseRepository.save(newCase);
    }

    private LocalDateTime calculateSlaDeadline(CasePriority priority, LocalDateTime startTime) {
        return switch (priority) {
            case CRITICAL -> startTime.plusDays(2);
            case HIGH -> startTime.plusDays(7);
            case MEDIUM -> startTime.plusDays(14);
            case LOW -> startTime.plusDays(30);
        };
    }

    /**
     * Daily job to increment 'daysOpen' for all non-closed cases
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 1 * * *") // Run at 1 AM
    @Transactional
    public void updateCaseAging() {
        log.info("Running daily case aging update...");
        java.util.List<CaseStatus> openStatuses = java.util.List.of(
                CaseStatus.NEW, CaseStatus.ASSIGNED, CaseStatus.IN_PROGRESS, CaseStatus.PENDING_INFO);

        java.util.List<ComplianceCase> activeCases = caseRepository.findByStatusIn(openStatuses);
        for (ComplianceCase c : activeCases) {
            if (c.getDaysOpen() == null) {
                c.setDaysOpen(1);
            } else {
                c.setDaysOpen(c.getDaysOpen() + 1);
            }
        }
        caseRepository.saveAll(activeCases);
        log.info("Updated aging for {} active cases", activeCases.size());
    }

    @Transactional
    public ComplianceCase assignCase(Long caseId) {
        ComplianceCase complianceCase = getCase(caseId);
        complianceCase.setStatus(CaseStatus.ASSIGNED);
        return caseRepository.save(complianceCase);
    }

    @Transactional
    public ComplianceCase updateStatus(Long caseId, CaseStatus status) {
        ComplianceCase complianceCase = getCase(caseId);
        complianceCase.setStatus(status);
        if (status == CaseStatus.CLOSED_CLEARED || status == CaseStatus.CLOSED_SAR_FILED
                || status == CaseStatus.CLOSED_BLOCKED) {
            if (complianceCase.getResolvedAt() == null) {
                complianceCase.setResolvedAt(LocalDateTime.now());
            }
        }
        return caseRepository.save(complianceCase);
    }

    @Transactional
    public ComplianceCase resolveCase(Long caseId, String resolutionNotes, String decision) {
        ComplianceCase complianceCase = getCase(caseId);

        complianceCase.setResolution(decision);
        complianceCase.setResolutionNotes(resolutionNotes);

        complianceCase.setStatus(CaseStatus.CLOSED_CLEARED); // Default to cleared
        complianceCase.setResolvedAt(LocalDateTime.now());

        return caseRepository.save(complianceCase);
    }

    @Transactional(readOnly = true)
    public ComplianceCase getCase(Long caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
    }

    @Transactional
    public void deleteCase(Long caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new IllegalArgumentException("Case not found: " + caseId);
        }
        caseRepository.deleteById(caseId);
    }
}
