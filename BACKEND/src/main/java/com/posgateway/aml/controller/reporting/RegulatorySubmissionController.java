package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.dto.reporting.RegulatorySubmissionDTO;
import com.posgateway.aml.dto.reporting.RegulatorySubmissionRequest;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.SubmissionStatus;
import com.posgateway.aml.service.reporting.RegulatorySubmissionService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Regulatory Submission Controller
 * REST endpoints for regulatory submission management
 */
@RestController
@RequestMapping("/api/reports")
public class RegulatorySubmissionController {

    private static final Logger logger = LoggerFactory.getLogger(RegulatorySubmissionController.class);

    private final RegulatorySubmissionService submissionService;
    private final PspIsolationService pspIsolationService;

    public RegulatorySubmissionController(RegulatorySubmissionService submissionService,
                                          PspIsolationService pspIsolationService) {
        this.submissionService = submissionService;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * List all submissions
     * GET /api/reports/submissions
     */
    @GetMapping("/submissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<Page<RegulatorySubmissionDTO>> listSubmissions(
            @RequestParam(required = false) Long pspId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String regulator,
            @RequestParam(required = false) String deadlineFrom,
            @RequestParam(required = false) String deadlineTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        
        logger.debug("List submissions - psp: {}, status: {}, regulator: {}", pspId, status, regulator);
        
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        SubmissionStatus statusFilter = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusFilter = SubmissionStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid status filter: {}", status);
            }
        }
        
        LocalDate deadlineFromDate = deadlineFrom != null ? LocalDate.parse(deadlineFrom) : null;
        LocalDate deadlineToDate = deadlineTo != null ? LocalDate.parse(deadlineTo) : null;
        
        Page<RegulatorySubmissionDTO> submissions = submissionService.listSubmissions(
            effectivePspId, statusFilter, regulator, deadlineFromDate, deadlineToDate,
            page, size, sortBy, sortDirection
        );
        
        return ResponseEntity.ok(submissions);
    }

    /**
     * Get submission by ID
     * GET /api/reports/submissions/{id}
     */
    @GetMapping("/submissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<RegulatorySubmissionDTO> getSubmission(@PathVariable Long id) {
        logger.debug("Get submission by ID: {}", id);
        
        RegulatorySubmissionDTO submission = submissionService.getSubmissionById(id);
        return ResponseEntity.ok(submission);
    }

    /**
     * Get submission status
     * GET /api/reports/submissions/{id}/status
     */
    @GetMapping("/submissions/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<Map<String, Object>> getSubmissionStatus(@PathVariable Long id) {
        logger.debug("Get submission status: {}", id);
        
        RegulatorySubmissionDTO submission = submissionService.getSubmissionById(id);
        
        Map<String, Object> status = Map.of(
            "id", submission.getId(),
            "submissionReference", submission.getSubmissionReference(),
            "status", submission.getStatus(),
            "statusDisplay", submission.getStatus().getDisplayName(),
            "canEdit", submission.getStatus().canEdit(),
            "canFile", submission.getStatus().canFile(),
            "isLateFiling", submission.getIsLateFiling(),
            "daysUntilDeadline", submission.getDaysUntilDeadline()
        );
        
        return ResponseEntity.ok(status);
    }

    /**
     * Prepare a new submission
     * POST /api/reports/{id}/prepare
     */
    @PostMapping("/{id}/prepare")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<RegulatorySubmissionDTO> prepareSubmission(@PathVariable Long id,
                                                                      @RequestBody Map<String, String> request,
                                                                      Authentication authentication) {
        String regulatoryBody = request.get("regulatoryBody");
        logger.info("Prepare submission for report: {}, regulator: {}", id, regulatoryBody);
        
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        Long pspId = pspIsolationService.getCurrentUserPspId();
        
        RegulatorySubmissionDTO submission = submissionService.prepareSubmission(id, regulatoryBody, userId, pspId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(submission);
    }

    /**
     * Submit to regulator
     * POST /api/reports/{id}/submit
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<RegulatorySubmissionDTO> submitToRegulator(@PathVariable Long id,
                                                                     @RequestBody RegulatorySubmissionRequest request,
                                                                     Authentication authentication) {
        logger.info("Submit report {} to regulator: {}", id, request.getRegulatorCode());
        
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        Long pspId = pspIsolationService.sanitizePspId(request.getPspId());
        
        RegulatorySubmissionDTO result;
        
        switch (request.getRegulatorCode().toUpperCase()) {
            case "FINCEN":
                result = submissionService.submitToFinCEN(id, userId, pspId);
                break;
            case "FCA":
                result = submissionService.submitToFCA(id, userId, pspId);
                break;
            case "OFAC":
                result = submissionService.submitToOFAC(id, userId, pspId);
                break;
            default:
                throw new IllegalArgumentException("Unsupported regulator: " + request.getRegulatorCode());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Submit to FinCEN
     * POST /api/reports/{id}/submit/fincen
     */
    @PostMapping("/{id}/submit/fincen")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<RegulatorySubmissionDTO> submitToFinCEN(@PathVariable Long id,
                                                                  Authentication authentication) {
        logger.info("Submit to FinCEN for report: {}", id);
        
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        Long pspId = pspIsolationService.getCurrentUserPspId();
        
        RegulatorySubmissionDTO result = submissionService.submitToFinCEN(id, userId, pspId);
        return ResponseEntity.ok(result);
    }

    /**
     * Submit to FCA
     * POST /api/reports/{id}/submit/fca
     */
    @PostMapping("/{id}/submit/fca")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<RegulatorySubmissionDTO> submitToFCA(@PathVariable Long id,
                                                               Authentication authentication) {
        logger.info("Submit to FCA for report: {}", id);
        
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        Long pspId = pspIsolationService.getCurrentUserPspId();
        
        RegulatorySubmissionDTO result = submissionService.submitToFCA(id, userId, pspId);
        return ResponseEntity.ok(result);
    }

    /**
     * Submit to OFAC
     * POST /api/reports/{id}/submit/ofac
     */
    @PostMapping("/{id}/submit/ofac")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<RegulatorySubmissionDTO> submitToOFAC(@PathVariable Long id,
                                                                Authentication authentication) {
        logger.info("Submit to OFAC for report: {}", id);
        
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        Long pspId = pspIsolationService.getCurrentUserPspId();
        
        RegulatorySubmissionDTO result = submissionService.submitToOFAC(id, userId, pspId);
        return ResponseEntity.ok(result);
    }

    /**
     * Update submission status
     * PUT /api/reports/submissions/{id}/status
     */
    @PutMapping("/submissions/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
    public ResponseEntity<RegulatorySubmissionDTO> updateSubmissionStatus(@PathVariable Long id,
                                                                          @RequestBody Map<String, String> request,
                                                                          Authentication authentication) {
        String statusStr = request.get("status");
        logger.info("Update submission {} status to: {}", id, statusStr);
        
        SubmissionStatus newStatus = SubmissionStatus.valueOf(statusStr.toUpperCase());
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        
        RegulatorySubmissionDTO result = submissionService.updateSubmissionStatus(id, newStatus, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Approve submission
     * POST /api/reports/submissions/{id}/approve
     */
    @PostMapping("/submissions/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'PSP_ADMIN')")
    public ResponseEntity<RegulatorySubmissionDTO> approveSubmission(@PathVariable Long id,
                                                                      Authentication authentication) {
        logger.info("Approve submission: {}", id);
        
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        
        RegulatorySubmissionDTO result = submissionService.updateSubmissionStatus(id, SubmissionStatus.APPROVED, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Reject submission
     * POST /api/reports/submissions/{id}/reject
     */
    @PostMapping("/submissions/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'PSP_ADMIN')")
    public ResponseEntity<RegulatorySubmissionDTO> rejectSubmission(@PathVariable Long id,
                                                                     @RequestBody(required = false) Map<String, String> request,
                                                                     Authentication authentication) {
        logger.info("Reject submission: {}", id);
        
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        
        RegulatorySubmissionDTO result = submissionService.updateSubmissionStatus(id, SubmissionStatus.REJECTED, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Create amended submission
     * POST /api/reports/submissions/{id}/amend
     */
    @PostMapping("/submissions/{id}/amend")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
    public ResponseEntity<RegulatorySubmissionDTO> amendSubmission(@PathVariable Long id,
                                                                    @RequestBody Map<String, String> request,
                                                                    Authentication authentication) {
        String reason = request.getOrDefault("reason", "Amendment required");
        logger.info("Amend submission: {}, reason: {}", id, reason);
        
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();
        
        RegulatorySubmissionDTO result = submissionService.amendSubmission(id, reason, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Get overdue submissions
     * GET /api/reports/submissions/overdue
     */
    @GetMapping("/submissions/overdue")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
    public ResponseEntity<List<RegulatorySubmissionDTO>> getOverdueSubmissions() {
        logger.debug("Get overdue submissions");
        
        List<RegulatorySubmissionDTO> overdue = submissionService.getOverdueSubmissions();
        return ResponseEntity.ok(overdue);
    }

    /**
     * Get at-risk submissions
     * GET /api/reports/submissions/at-risk
     */
    @GetMapping("/submissions/at-risk")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN')")
    public ResponseEntity<List<RegulatorySubmissionDTO>> getAtRiskSubmissions(
            @RequestParam(defaultValue = "7") int daysThreshold) {
        
        logger.debug("Get at-risk submissions within {} days", daysThreshold);
        
        List<RegulatorySubmissionDTO> atRisk = submissionService.getAtRiskSubmissions(daysThreshold);
        return ResponseEntity.ok(atRisk);
    }

    /**
     * Delete submission (draft only)
     * DELETE /api/reports/submissions/{id}
     */
    @DeleteMapping("/submissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'PSP_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteSubmission(@PathVariable Long id) {
        logger.info("Delete submission: {}", id);
        
        submissionService.deleteSubmission(id);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Submission deleted successfully"
        ));
    }

    /**
     * Track submission status (by reference)
     * GET /api/reports/submissions/reference/{reference}/status
     */
    @GetMapping("/submissions/reference/{reference}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<RegulatorySubmissionDTO> trackSubmissionStatus(@PathVariable String reference) {
        logger.debug("Track submission status: {}", reference);
        
        RegulatorySubmissionDTO status = submissionService.trackSubmissionStatus(reference);
        return ResponseEntity.ok(status);
    }

    /**
     * Get submission statistics
     * GET /api/reports/submissions/statistics
     */
    @GetMapping("/submissions/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'PSP_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSubmissionStatistics(
            @RequestParam(required = false) Long pspId) {
        
        logger.debug("Get submission statistics for psp: {}", pspId);
        
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        // Get all submissions for PSP
        Page<RegulatorySubmissionDTO> all = submissionService.listSubmissions(
            effectivePspId, null, null, null, null, 0, Integer.MAX_VALUE, "createdAt", "DESC"
        );
        
        long draft = all.getContent().stream().filter(s -> s.getStatus() == SubmissionStatus.DRAFT).count();
        long pending = all.getContent().stream().filter(s -> s.getStatus() == SubmissionStatus.PENDING_REVIEW).count();
        long approved = all.getContent().stream().filter(s -> s.getStatus() == SubmissionStatus.APPROVED).count();
        long filed = all.getContent().stream().filter(s -> s.getStatus() == SubmissionStatus.FILED).count();
        long rejected = all.getContent().stream().filter(s -> s.getStatus() == SubmissionStatus.REJECTED).count();
        long amended = all.getContent().stream().filter(s -> s.getStatus() == SubmissionStatus.AMENDED).count();
        long overdue = all.getContent().stream()
            .filter(s -> s.getIsLateFiling() != null && s.getIsLateFiling())
            .count();
        
        Map<String, Object> statistics = Map.of(
            "total", all.getTotalElements(),
            "byStatus", Map.of(
                "draft", draft,
                "pendingReview", pending,
                "approved", approved,
                "filed", filed,
                "rejected", rejected,
                "amended", amended
            ),
            "overdue", overdue,
            "atRisk", all.getContent().stream()
                .filter(s -> s.getDaysUntilDeadline() != null && s.getDaysUntilDeadline() <= 7 && s.getDaysUntilDeadline() >= 0)
                .count()
        );
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * Get available regulators
     * GET /api/reports/regulators
     */
    @GetMapping("/regulators")
    @PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER', 'ANALYST')")
    public ResponseEntity<List<Map<String, String>>> getAvailableRegulators() {
        logger.debug("Get available regulators");
        
        List<Map<String, String>> regulators = List.of(
            Map.of("code", "FINCEN", "name", "FinCEN (US)", "jurisdiction", "US", 
                   "description", "Financial Crimes Enforcement Network"),
            Map.of("code", "FCA", "name", "FCA (UK)", "jurisdiction", "UK",
                   "description", "Financial Conduct Authority"),
            Map.of("code", "OFAC", "name", "OFAC (US)", "jurisdiction", "US",
                   "description", "Office of Foreign Assets Control"),
            Map.of("code", "FIU", "name", "FIU", "jurisdiction", "GENERAL",
                   "description", "Financial Intelligence Unit")
        );
        
        return ResponseEntity.ok(regulators);
    }
}
