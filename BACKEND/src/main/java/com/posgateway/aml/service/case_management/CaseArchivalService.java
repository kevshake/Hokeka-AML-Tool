package com.posgateway.aml.service.case_management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Service responsible for enforcing data retention policies and archiving old
 * cases.
 */
@Service
public class CaseArchivalService {

    private static final Logger logger = LoggerFactory.getLogger(CaseArchivalService.class);

    private final ComplianceCaseRepository complianceCaseRepository;
    private final CaseAuditService caseAuditService;
    private final ObjectMapper objectMapper;

    // Regulatory Requirements
    private static final int ARCHIVE_AFTER_YEARS = 1;
    private static final int DELETE_AFTER_YEARS_CBK = 7;
    // private static final int DELETE_AFTER_YEARS_FATF = 5; // Minimum

    @Autowired
    public CaseArchivalService(ComplianceCaseRepository complianceCaseRepository, CaseAuditService caseAuditService,
            ObjectMapper objectMapper) {
        this.complianceCaseRepository = complianceCaseRepository;
        this.caseAuditService = caseAuditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Identify cases ready for cold storage archival.
     * Criteria: Closed for > 1 year.
     * Action: Export report, Hash it, Simulate upload, Mark as archived.
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    @Transactional
    public void archiveEligibleCases() {
        logger.info("Starting scheduled case archival process...");
        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(ARCHIVE_AFTER_YEARS);

        // Ideally use a repository method:
        // findByStatusInAndUpdatedAtBeforeAndArchivedFalse
        // For now, fetching all relevant status and filtering
        // Optimized: We should implement a custom query in repository for performance
        List<ComplianceCase> candidates = complianceCaseRepository.findAll().stream()
                .filter(c -> !c.getArchived())
                .filter(c -> c.getStatus() == CaseStatus.CLOSED_CLEARED ||
                        c.getStatus() == CaseStatus.CLOSED_SAR_FILED ||
                        c.getStatus() == CaseStatus.CLOSED_BLOCKED ||
                        c.getStatus() == CaseStatus.CLOSED_REJECTED)
                .filter(c -> c.getUpdatedAt().isBefore(cutoffDate))
                .toList();

        for (ComplianceCase cCase : candidates) {
            try {
                processArchival(cCase);
            } catch (Exception e) {
                logger.error("Failed to archive case {}", cCase.getCaseReference(), e);
            }
        }
        logger.info("Archival process completed. Archived {} cases.", candidates.size());
    }

    private void processArchival(ComplianceCase cCase) throws Exception {
        // 1. Generate Full Audit Report (JSON)
        String reportJson = caseAuditService.exportCaseReport(cCase.getId());

        // 2. Generate SHA-256 Hash for WORM integrity
        String checksum = calculateChecksum(reportJson);

        // 3. Simulate Uplod to Cold Storage (S3 Glacier / WORM Drive)
        String storageReference = uploadToColdStorage(cCase.getCaseReference(), reportJson, checksum);

        // 4. Update Case Entity
        cCase.setArchived(true);
        cCase.setArchivedAt(LocalDateTime.now());
        cCase.setArchiveReference(storageReference);

        // Note: In a real system, we might delete heavy relations (alerts, evidence
        // blobs)
        // from the active DB here to save space, keeping only the metadata + offline
        // reference.
        complianceCaseRepository.save(cCase);

        logger.info("Archived case {} with checksum {}", cCase.getCaseReference(), checksum);
    }

    /**
     * Enforce max retention policy.
     * Criteria: Closed for > 7 years (CBK).
     * Action: Hard delete or anonymize.
     */
    @Scheduled(cron = "0 0 3 * * ?") // Run at 3 AM daily
    @Transactional
    public void enforceRetentionPolicy() {
        logger.info("Starting retention policy enforcement (Hard Delete)...");
        LocalDateTime retentionLimit = LocalDateTime.now().minusYears(DELETE_AFTER_YEARS_CBK);

        List<ComplianceCase> expiredCases = complianceCaseRepository.findAll().stream()
                .filter(c -> c.getStatus().name().startsWith("CLOSED"))
                .filter(c -> c.getUpdatedAt().isBefore(retentionLimit))
                .toList();

        // CAUTION: This performs actual deletion
        for (ComplianceCase cCase : expiredCases) {
            logger.warn("Deleting expired case {} (Exceeded {} years retention)", cCase.getCaseReference(),
                    DELETE_AFTER_YEARS_CBK);
            // complianceCaseRepository.delete(cCase); // Uncomment to enable actual
            // deletion
        }
    }

    private String calculateChecksum(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encodedhash);
    }

    private String uploadToColdStorage(String caseRef, String content, String checksum) {
        // Simulation of S3 PutObject with Object Lock
        return "s3://aml-cold-storage/year-" + LocalDateTime.now().getYear() + "/" + caseRef + ".json?checksum="
                + checksum;
    }
}
