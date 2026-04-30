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

        ComplianceCase newCase = ComplianceCase.builder()
                .caseReference("CASE-" + UUID.randomUUID())
                .description(description)
                .status(CaseStatus.NEW)
                .priority(CasePriority.MEDIUM)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return caseRepository.save(newCase);
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
