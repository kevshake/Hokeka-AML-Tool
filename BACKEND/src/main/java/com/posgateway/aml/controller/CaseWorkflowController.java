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
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'PSP_ADMIN', 'PSP_USER')")
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
    private final com.posgateway.aml.repository.ComplianceCaseRepository caseRepository;
    private final com.posgateway.aml.service.security.PspIsolationService pspIsolationService;
    /** Live pull of the case transactions' details when the timeline / graph is viewed (R6). */
    private final com.posgateway.aml.service.edgedata.CaseTransactionDetailsEnricher edgeDetails;

    @Autowired
    public CaseWorkflowController(CaseTimelineService timelineService,
            CaseNetworkService networkService,
            CaseDecisionService decisionService,
            CaseEscalationService escalationService,
            CaseAssignmentService assignmentService,
            UserRepository userRepository,
            CasePermissionService permissionService,
            CaseAuditService auditService,
            com.posgateway.aml.repository.ComplianceCaseRepository caseRepository,
            com.posgateway.aml.service.security.PspIsolationService pspIsolationService,
            com.posgateway.aml.service.edgedata.CaseTransactionDetailsEnricher edgeDetails) {
        this.edgeDetails = edgeDetails;
        this.caseRepository = caseRepository;
        this.pspIsolationService = pspIsolationService;
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
    public ResponseEntity<CaseTimelineService.CaseTimelineDTO> getCaseTimeline(@PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean transactionDetails,
            @RequestParam(required = false) String supportReason,
            @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader) {
        User currentUser = getCurrentUser();
        if (!permissionService.canView(id, currentUser)) {
            return ResponseEntity.status(403).build();
        }
        CaseTimelineService.CaseTimelineDTO timeline = timelineService.buildTimeline(id);
        // R6: transaction details pulled live per view (never persisted).
        Long casePsp = caseRepository.findById(id).map(com.posgateway.aml.entity.compliance.ComplianceCase::getPspId)
                .orElse(null);
        if (casePsp != null) {
            edgeDetails.enrichTimeline(casePsp, id, timeline, transactionDetails, reason(supportReason, reasonHeader));
        } else {
            timeline.setTransactionDetailsStatus(com.posgateway.aml.dto.edgedata.EdgePullStatus.none());
        }
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(timeline);
    }

    /** The support reason from the parameter, else the header. */
    private static String reason(String param, String header) {
        return param != null && !param.isBlank() ? param : header;
    }

    /**
     * Get Case Network Graph
     * GET /api/cases/{id}/graph
     */
    @GetMapping("/{id}/graph")
    public ResponseEntity<CaseNetworkService.NetworkGraphDTO> getCaseGraph(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int depth,
            @RequestParam(defaultValue = "true") boolean transactionDetails,
            @RequestParam(required = false) java.util.List<String> related,
            @RequestParam(required = false) Integer relatedDepth,
            @RequestParam(required = false) Integer relatedWindowDays,
            @RequestParam(required = false) String supportReason,
            @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader) {
        User currentUser = getCurrentUser();
        if (!permissionService.canView(id, currentUser)) {
            return ResponseEntity.status(403).build();
        }
        CaseNetworkService.NetworkGraphDTO graph = networkService.buildNetworkGraph(id, depth);
        // R6: transaction details (batchGet) and, when asked, txn.related links pulled live per view.
        Long casePsp = caseRepository.findById(id).map(com.posgateway.aml.entity.compliance.ComplianceCase::getPspId)
                .orElse(null);
        if (casePsp != null) {
            edgeDetails.enrichGraph(casePsp, id, graph, transactionDetails, related, relatedDepth, relatedWindowDays,
                    reason(supportReason, reasonHeader));
        } else {
            graph.setTransactionDetailsStatus(com.posgateway.aml.dto.edgedata.EdgePullStatus.none());
        }
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(graph);
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
     * Escalate a case — the single handler for {@code POST /cases/{id}/escalate}.
     *
     * <p>404 when the case does not exist, 403 when it belongs to another PSP or the caller's role may
     * not escalate it ({@link CasePermissionService#canAct}), 400 without a reason. On success the
     * escalation is applied by {@link CaseEscalationService} and recorded in the case audit trail.
     */
    @PostMapping("/{id}/escalate")
    public ResponseEntity<Map<String, Object>> escalateCase(@PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String reason = request == null ? null : request.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required to escalate a case");
        }
        com.posgateway.aml.entity.compliance.ComplianceCase kase = caseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + id));
        pspIsolationService.validatePspAccess(kase.getPspId());

        User currentUser = getCurrentUser();
        if (!permissionService.canAct(id, currentUser, "ESCALATE")) {
            return ResponseEntity.status(403).build();
        }

        escalationService.escalateCase(id, reason.trim(), currentUser.getId());
        auditService.logAction(id, currentUser, "ESCALATE", reason.trim());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "caseId", id,
                "message", "Case escalated successfully"));
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
