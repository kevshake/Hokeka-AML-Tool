package com.posgateway.aml.controller.compliance;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;

import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.SarWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for SAR Workflow
 * 
 * Security: Role-based access for SAR operations
 * - CREATE_SAR: Investigators, Compliance Officers
 * - APPROVE_SAR: MLRO only
 * - FILE_SAR: MLRO only
 */
// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/compliance/sar/workflow")
@PreAuthorize("hasAnyRole('ADMIN', 'MLRO', 'COMPLIANCE_OFFICER', 'INVESTIGATOR')")
public class SarWorkflowController {

    private final SarWorkflowService sarWorkflowService;
    private final UserRepository userRepository;

    public SarWorkflowController(SarWorkflowService sarWorkflowService, UserRepository userRepository) {
        this.sarWorkflowService = sarWorkflowService;
        this.userRepository = userRepository;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('CREATE_SAR')")
    public ResponseEntity<SuspiciousActivityReport> createSar(@RequestBody CreateSarRequest request) {
        User creator = fetchUser(request.getCreatorUserId());
        SuspiciousActivityReport sar = new SuspiciousActivityReport();
        sar.setSarReference(request.getSarReference());
        sar.setNarrative(request.getNarrative());
        sar.setSuspiciousActivityType(request.getSuspiciousActivityType());
        sar.setJurisdiction(request.getJurisdiction() != null ? request.getJurisdiction() : "UNKNOWN");
        sar.setSarType(request.getSarType());
        SuspiciousActivityReport created = sarWorkflowService.createSarDraft(sar, creator);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('CREATE_SAR')")
    public ResponseEntity<SuspiciousActivityReport> submitForReview(@RequestBody IdRequest request) {
        User user = fetchUser(request.getUserId());
        SuspiciousActivityReport updated = sarWorkflowService.submitForReview(request.getSarId(), user);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAuthority('APPROVE_SAR')")
    public ResponseEntity<SuspiciousActivityReport> approve(@RequestBody IdRequest request) {
        User approver = fetchUser(request.getUserId());
        SuspiciousActivityReport updated = sarWorkflowService.approveSar(request.getSarId(), approver);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/reject")
    @PreAuthorize("hasAuthority('APPROVE_SAR')")
    public ResponseEntity<SuspiciousActivityReport> reject(@RequestBody RejectRequest request) {
        User rejector = fetchUser(request.getUserId());
        SuspiciousActivityReport updated = sarWorkflowService.rejectSar(request.getSarId(), rejector,
                request.getReason());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/file")
    @PreAuthorize("hasAuthority('FILE_SAR')")
    public ResponseEntity<SuspiciousActivityReport> file(@RequestBody FileSarRequest request) {
        User filer = fetchUser(request.getUserId());
        SuspiciousActivityReport updated = sarWorkflowService.markAsFiled(request.getSarId(),
                request.getFilingReference(), filer);
        return ResponseEntity.ok(updated);
    }

    private User fetchUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    public static class CreateSarRequest {
        private String sarReference;
        private String narrative;
        private String suspiciousActivityType;
        private String jurisdiction;
        private com.posgateway.aml.model.SarType sarType = com.posgateway.aml.model.SarType.INITIAL;
        private Long creatorUserId;

        public CreateSarRequest() {
        }

        public String getSarReference() {
            return sarReference;
        }

        public void setSarReference(String sarReference) {
            this.sarReference = sarReference;
        }

        public String getNarrative() {
            return narrative;
        }

        public void setNarrative(String narrative) {
            this.narrative = narrative;
        }

        public String getSuspiciousActivityType() {
            return suspiciousActivityType;
        }

        public void setSuspiciousActivityType(String suspiciousActivityType) {
            this.suspiciousActivityType = suspiciousActivityType;
        }

        public String getJurisdiction() {
            return jurisdiction;
        }

        public void setJurisdiction(String jurisdiction) {
            this.jurisdiction = jurisdiction;
        }

        public com.posgateway.aml.model.SarType getSarType() {
            return sarType;
        }

        public void setSarType(com.posgateway.aml.model.SarType sarType) {
            this.sarType = sarType;
        }

        public Long getCreatorUserId() {
            return creatorUserId;
        }

        public void setCreatorUserId(Long creatorUserId) {
            this.creatorUserId = creatorUserId;
        }
    }

    public static class IdRequest {
        private Long sarId;
        private Long userId;

        public IdRequest() {
        }

        public Long getSarId() {
            return sarId;
        }

        public void setSarId(Long sarId) {
            this.sarId = sarId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }

    public static class RejectRequest {
        private Long sarId;
        private Long userId;
        private String reason;

        public RejectRequest() {
        }

        public Long getSarId() {
            return sarId;
        }

        public void setSarId(Long sarId) {
            this.sarId = sarId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class FileSarRequest {
        private Long sarId;
        private Long userId;
        private String filingReference;

        public FileSarRequest() {
        }

        public Long getSarId() {
            return sarId;
        }

        public void setSarId(Long sarId) {
            this.sarId = sarId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getFilingReference() {
            return filingReference;
        }

        public void setFilingReference(String filingReference) {
            this.filingReference = filingReference;
        }
    }
}
