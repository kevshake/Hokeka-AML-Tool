package com.posgateway.aml.controller;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.case_management.CaseAssignmentService;
import com.posgateway.aml.service.case_management.CaseDecisionService;
import com.posgateway.aml.service.case_management.CaseEscalationService;
import com.posgateway.aml.service.case_management.CaseNetworkService;
import com.posgateway.aml.service.case_management.CaseTimelineService;
import com.posgateway.aml.service.case_management.CasePermissionService;
import com.posgateway.aml.service.case_management.CaseAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Investigator Workflow.
 * Exposes services for Case Review UI: Timeline, Graph, Decisions, Actions.
 */
@RestController
@RequestMapping("/cases")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR')")
public class CaseWorkflowController {

    private static final Logger logger = LoggerFactory.getLogger(CaseWorkflowController.class);

    private final CaseTimelineService timelineService;
    private final CaseNetworkService networkService;
    private final CaseDecisionService decisionService;
    private final CaseEscalationService escalationService;
    private final CaseAssignmentService assignmentService;
    private final UserRepository userRepository;
    private final CasePermissionService permissionService;
    private final CaseAuditService auditService;

    @Autowired
    public CaseWorkflowController(CaseTimelineService timelineService,
            CaseNetworkService networkService,
            CaseDecisionService decisionService,
            CaseEscalationService escalationService,
            CaseAssignmentService assignmentService,
            UserRepository userRepository,
            CasePermissionService permissionService,
            CaseAuditService auditService) {
        this.timelineService = timelineService;
        this.networkService = networkService;
        this.decisionService = decisionService;
        this.escalationService = escalationService;
        this.assignmentService = assignmentService;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    /**
     * Get Case Timeline
     * GET /api/cases/{id}/timeline
     */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<CaseTimelineService.CaseTimelineDTO> getCaseTimeline(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (!permissionService.canView(id, currentUser)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(timelineService.buildTimeline(id));
    }

    /**
     * Get Case Network Graph
     * GET /api/cases/{id}/graph
     */
    @GetMapping("/{id}/graph")
    public ResponseEntity<CaseNetworkService.NetworkGraphDTO> getCaseGraph(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int depth) {
        User currentUser = getCurrentUser();
        if (!permissionService.canView(id, currentUser)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(networkService.buildNetworkGraph(id, depth));
    }

    /**
     * Make a Decision (Approve, Reject, SAR, etc.)
     * POST /api/cases/{id}/decisions
     */
    @PostMapping("/{id}/decisions")
    public ResponseEntity<Void> makeDecision(@PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String decisionType = request.get("decisionType");
        String justification = request.get("justification");

        User currentUser = getCurrentUser();

        // RBAC Check
        if (!permissionService.canAct(id, currentUser, decisionType)) {
            return ResponseEntity.status(403).build();
        }

        decisionService.makeDecision(id, decisionType, justification, currentUser);

        // Audit Log
        auditService.logAction(id, currentUser, "DECISION_" + decisionType, justification);

        return ResponseEntity.ok().build();
    }

    /**
     * Escalate Case
     * POST /api/cases/{id}/escalate
     */
    @PostMapping("/{id}/escalate")
    public ResponseEntity<Void> escalateCase(@PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String reason = request.get("reason");

        User currentUser = getCurrentUser();

        // RBAC Check
        if (!permissionService.canAct(id, currentUser, "ESCALATE")) {
            return ResponseEntity.status(403).build();
        }

        escalationService.escalateCase(id, reason, currentUser.getId());

        // Audit Log
        auditService.logAction(id, currentUser, "ESCALATE", reason);

        return ResponseEntity.ok().build();
    }

    /**
     * Assign Case
     * POST /api/cases/{id}/assign
     */
    @PostMapping("/{id}/assign")
    public ResponseEntity<Void> assignCase(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        String assignToUsername = (String) request.get("assignTo");
        User currentUser = getCurrentUser();

        // RBAC Check
        if (!permissionService.canAct(id, currentUser, "ASSIGN")) {
            return ResponseEntity.status(403).build();
        }

        if (assignToUsername != null) {
            User targetUser = userRepository.findByUsername(assignToUsername)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            assignmentService.assignCaseToUser(id, targetUser.getId(), currentUser.getId());
            auditService.logAction(id, currentUser, "ASSIGN", "Assigned to " + assignToUsername);
        } else {
            // Assign to self
            assignmentService.assignCaseToUser(id, currentUser.getId(), currentUser.getId());
            auditService.logAction(id, currentUser, "ASSIGN_SELF", "Picked up case");
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Case Replay (Audit Trail)
     * GET /api/cases/{id}/audit/replay
     */
    @GetMapping("/{id}/audit/replay")
    public ResponseEntity<?> getCaseReplay(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        // Strict check: Only Auditors/Admins or specified roles should view Replay?
        // Reusing canView for now, but usually more strict.
        if (!permissionService.canView(id, currentUser)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(auditService.replayCase(id));
    }

    // Helper to extract User from security context
    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username;
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Current user not found in DB"));
    }
}
