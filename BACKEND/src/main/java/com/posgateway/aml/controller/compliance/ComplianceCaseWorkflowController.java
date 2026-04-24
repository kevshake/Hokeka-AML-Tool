package com.posgateway.aml.controller.compliance;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.CaseWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Compliance Case Workflow
 */
// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/compliance/cases/workflow")
public class ComplianceCaseWorkflowController {

    private final CaseWorkflowService caseWorkflowService;
    private final UserRepository userRepository;

    public ComplianceCaseWorkflowController(CaseWorkflowService caseWorkflowService, UserRepository userRepository) {
        this.caseWorkflowService = caseWorkflowService;
        this.userRepository = userRepository;
    }

    @PostMapping("/create")
    public ResponseEntity<ComplianceCase> createCase(@RequestBody CreateCaseRequest request) {
        User creator = request.getCreatorUserId() != null
                ? fetchUser(request.getCreatorUserId())
                : getAuthenticatedUser();
        if (creator == null) {
            return ResponseEntity.badRequest().build();
        }
        ComplianceCase created = caseWorkflowService.createCase(
                request.getCaseReference(),
                request.getDescription(),
                request.getPriority() != null ? CasePriority.valueOf(request.getPriority()) : CasePriority.MEDIUM,
                creator);
        return ResponseEntity.ok(created);
    }

    private User getAuthenticatedUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getPrincipal())) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    @PostMapping("/assign")
    public ResponseEntity<ComplianceCase> assignCase(@RequestBody AssignCaseRequest request) {
        User assigner = fetchUser(request.getAssignerUserId());
        ComplianceCase updated = caseWorkflowService.assignCase(
                request.getCaseId(),
                request.getAssigneeUserId(),
                assigner);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/status")
    public ResponseEntity<ComplianceCase> updateStatus(@RequestBody UpdateStatusRequest request) {
        User user = fetchUser(request.getUserId());
        ComplianceCase updated = caseWorkflowService.updateStatus(
                request.getCaseId(),
                CaseStatus.valueOf(request.getStatus()),
                user);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/escalate")
    public ResponseEntity<ComplianceCase> escalate(@RequestBody EscalateCaseRequest request) {
        User user = fetchUser(request.getUserId());
        ComplianceCase updated = caseWorkflowService.escalateCase(
                request.getCaseId(),
                request.getEscalatedToUserId(),
                request.getReason(),
                user);
        return ResponseEntity.ok(updated);
    }

    private User fetchUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    public static class CreateCaseRequest {
        private String caseReference;
        private String description;
        private String priority;
        private Long creatorUserId;

        public CreateCaseRequest() {
        }

        public String getCaseReference() {
            return caseReference;
        }

        public void setCaseReference(String caseReference) {
            this.caseReference = caseReference;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public Long getCreatorUserId() {
            return creatorUserId;
        }

        public void setCreatorUserId(Long creatorUserId) {
            this.creatorUserId = creatorUserId;
        }
    }

    public static class AssignCaseRequest {
        private Long caseId;
        private Long assigneeUserId;
        private Long assignerUserId;

        public AssignCaseRequest() {
        }

        public Long getCaseId() {
            return caseId;
        }

        public void setCaseId(Long caseId) {
            this.caseId = caseId;
        }

        public Long getAssigneeUserId() {
            return assigneeUserId;
        }

        public void setAssigneeUserId(Long assigneeUserId) {
            this.assigneeUserId = assigneeUserId;
        }

        public Long getAssignerUserId() {
            return assignerUserId;
        }

        public void setAssignerUserId(Long assignerUserId) {
            this.assignerUserId = assignerUserId;
        }
    }

    public static class UpdateStatusRequest {
        private Long caseId;
        private String status;
        private Long userId;

        public UpdateStatusRequest() {
        }

        public Long getCaseId() {
            return caseId;
        }

        public void setCaseId(Long caseId) {
            this.caseId = caseId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }

    public static class EscalateCaseRequest {
        private Long caseId;
        private Long escalatedToUserId;
        private String reason;
        private Long userId;

        public EscalateCaseRequest() {
        }

        public Long getCaseId() {
            return caseId;
        }

        public void setCaseId(Long caseId) {
            this.caseId = caseId;
        }

        public Long getEscalatedToUserId() {
            return escalatedToUserId;
        }

        public void setEscalatedToUserId(Long escalatedToUserId) {
            this.escalatedToUserId = escalatedToUserId;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }
}
