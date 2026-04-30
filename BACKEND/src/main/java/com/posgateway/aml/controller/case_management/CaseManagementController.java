package com.posgateway.aml.controller.case_management;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.CaseQueue;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.service.case_management.CaseActivityService;
import com.posgateway.aml.service.case_management.CaseAssignmentService;
import com.posgateway.aml.service.case_management.CaseEscalationService;
import com.posgateway.aml.service.case_management.CaseQueueService;
import com.posgateway.aml.service.case_management.CaseSlaService;
import com.posgateway.aml.service.case_management.CaseTimelineService;
import com.posgateway.aml.service.analytics.ComplianceDashboardService;
import com.posgateway.aml.service.analytics.OperationalMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Case Management Controller
 * Provides endpoints for case management operations
 */
@RestController
@RequestMapping("/cases")
@Tag(name = "Case Management", description = "APIs for managing compliance cases, timelines, assignments, and escalations")
public class CaseManagementController {

    private final CaseActivityService caseActivityService;
    private final CaseAssignmentService caseAssignmentService;
    private final CaseSlaService caseSlaService;
    private final CaseEscalationService caseEscalationService;
    private final CaseQueueService caseQueueService;
    private final CaseTimelineService caseTimelineService;
    private final ComplianceDashboardService dashboardService;
    private final OperationalMetricsService metricsService;
    private final ComplianceCaseRepository caseRepository;

    @Autowired
    public CaseManagementController(CaseActivityService caseActivityService,
                                   CaseAssignmentService caseAssignmentService,
                                   CaseSlaService caseSlaService,
                                   CaseEscalationService caseEscalationService,
                                   CaseQueueService caseQueueService,
                                   CaseTimelineService caseTimelineService,
                                   ComplianceDashboardService dashboardService,
                                   OperationalMetricsService metricsService,
                                   ComplianceCaseRepository caseRepository) {
        this.caseActivityService = caseActivityService;
        this.caseAssignmentService = caseAssignmentService;
        this.caseSlaService = caseSlaService;
        this.caseEscalationService = caseEscalationService;
        this.caseQueueService = caseQueueService;
        this.caseTimelineService = caseTimelineService;
        this.dashboardService = dashboardService;
        this.metricsService = metricsService;
        this.caseRepository = caseRepository;
    }

    /**
     * Get case timeline
     */
    @Operation(
            summary = "Get case timeline",
            description = "Retrieves a chronological timeline of all events related to a specific case, including creation, assignments, notes, escalations, and transactions."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Timeline retrieved successfully",
                    content = @Content(schema = @Schema(implementation = CaseTimelineService.CaseTimelineDTO.class))),
            @ApiResponse(responseCode = "404", description = "Case not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{caseId}/timeline")
    public ResponseEntity<CaseTimelineService.CaseTimelineDTO> getCaseTimeline(
            @Parameter(description = "Case ID", required = true, example = "1")
            @PathVariable Long caseId) {
        return ResponseEntity.ok(caseTimelineService.buildTimeline(caseId));
    }

    /**
     * Get case activity feed
     */
    @Operation(
            summary = "Get case activity feed",
            description = "Retrieves a paginated feed of activities for a specific case, including user actions, status changes, and system events."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Activity feed retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Case not found")
    })
    @GetMapping("/{caseId}/activities")
    public ResponseEntity<?> getCaseActivities(
            @Parameter(description = "Case ID", required = true, example = "1")
            @PathVariable Long caseId,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(caseActivityService.getActivityFeed(
                caseId,
                org.springframework.data.domain.PageRequest.of(page, size)
        ));
    }

    /**
     * Assign case automatically
     */
    @PostMapping("/{caseId}/assign/auto")
    public ResponseEntity<Map<String, Object>> autoAssignCase(@PathVariable Long caseId,
                                                             @RequestParam String role) {
        ComplianceCase complianceCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));
        
        com.posgateway.aml.model.UserRole userRole = com.posgateway.aml.model.UserRole.valueOf(role);
        var assignedUser = caseAssignmentService.assignCaseByWorkload(complianceCase, userRole);
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "assignedTo", assignedUser.getUsername(),
                "message", "Case assigned successfully"
        ));
    }

    /**
     * Escalate case
     */
    @PostMapping("/{caseId}/escalate")
    public ResponseEntity<Map<String, Object>> escalateCase(@PathVariable Long caseId,
                                                            @RequestBody EscalationRequest request,
                                                            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user) {
        Long userId = Long.parseLong(user.getUsername()); // Assuming username is user ID
        caseEscalationService.escalateCase(
                caseId,
                request.getReason(),
                userId
        );
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Case escalated successfully"
        ));
    }

    /**
     * Get SLA status
     */
    @GetMapping("/{caseId}/sla")
    public ResponseEntity<Map<String, Object>> getSlaStatus(@PathVariable Long caseId) {
        ComplianceCase complianceCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));
        
        CaseSlaService.CaseSlaStatus slaStatus = caseSlaService.checkSlaStatus(complianceCase);
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("slaStatus", slaStatus.name());
        response.put("slaDeadline", complianceCase.getSlaDeadline());
        response.put("daysOpen", complianceCase.getDaysOpen() != null ? complianceCase.getDaysOpen() : 0);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get compliance dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ComplianceDashboardService.ComplianceDashboardDTO> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboardMetrics());
    }

    /**
     * Get operational metrics
     */
    @GetMapping("/metrics/operational")
    public ResponseEntity<Map<String, Object>> getOperationalMetrics(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        
        if (startDate == null) {
            startDate = LocalDateTime.now().minusMonths(1);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        return ResponseEntity.ok(Map.of(
                "averageInvestigationTime", metricsService.calculateAverageInvestigationTime(startDate, endDate),
                "sarFilingMetrics", metricsService.getSarFilingMetrics(startDate, endDate)
        ));
    }

    /**
     * Get all case queues
     */
    @GetMapping("/queues")
    public ResponseEntity<?> getAllQueues() {
        return ResponseEntity.ok(caseQueueService.getAllQueues());
    }

    /**
     * Get case queues with computed stats (safe DTO for UI)
     */
    @GetMapping("/queues/overview")
    public ResponseEntity<List<CaseQueueOverview>> getQueuesOverview() {
        List<CaseQueue> queues = caseQueueService.getAllQueues();
        List<CaseQueueOverview> overview = queues.stream()
                .map(q -> {
                    long queuedNewCount = 0;
                    try {
                        queuedNewCount = caseRepository.countByQueueAndStatus(q, CaseStatus.NEW);
                    } catch (Exception ignored) {
                        // If counting fails, default to 0 and keep UI responsive
                    }
                    return CaseQueueOverview.from(q, queuedNewCount);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(overview);
    }

    /**
     * Create a new case queue
     */
    @PostMapping("/queues")
    public ResponseEntity<CaseQueueOverview> createQueue(@RequestBody CreateQueueRequest request) {
        if (request == null || request.getQueueName() == null || request.getQueueName().trim().isEmpty()) {
            throw new IllegalArgumentException("queueName is required");
        }
        if (request.getTargetRole() == null || request.getTargetRole().trim().isEmpty()) {
            throw new IllegalArgumentException("targetRole is required");
        }

        // Validate role exists
        UserRole.valueOf(request.getTargetRole().trim().toUpperCase());

        CasePriority minPriority = null;
        if (request.getMinPriority() != null && !request.getMinPriority().trim().isEmpty()) {
            minPriority = CasePriority.valueOf(request.getMinPriority().trim().toUpperCase());
        }

        Integer maxQueueSize = request.getMaxQueueSize();
        if (maxQueueSize != null && maxQueueSize <= 0) {
            maxQueueSize = null;
        }

        CaseQueue created = caseQueueService.createQueue(
                request.getQueueName().trim(),
                request.getTargetRole().trim().toUpperCase(),
                minPriority,
                maxQueueSize,
                request.getAutoAssign()
        );

        if (request.getEnabled() != null) {
            created = caseQueueService.setQueueEnabled(created.getId(), request.getEnabled());
        }

        long queuedNewCount = 0;
        try {
            queuedNewCount = caseRepository.countByQueueAndStatus(created, CaseStatus.NEW);
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(CaseQueueOverview.from(created, queuedNewCount));
    }

    /**
     * Update a case queue (currently supports enable/disable and auto-assign toggles)
     */
    @PatchMapping("/queues/{queueId}")
    public ResponseEntity<CaseQueueOverview> updateQueue(@PathVariable Long queueId, @RequestBody UpdateQueueRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        String targetRole = null;
        if (request.getTargetRole() != null && !request.getTargetRole().trim().isEmpty()) {
            targetRole = request.getTargetRole().trim().toUpperCase();
            UserRole.valueOf(targetRole); // validate
        }

        CasePriority minPriority = null;
        if (request.getMinPriority() != null && !request.getMinPriority().trim().isEmpty()) {
            minPriority = CasePriority.valueOf(request.getMinPriority().trim().toUpperCase());
        }

        Integer maxQueueSize = request.getMaxQueueSize();
        if (maxQueueSize != null && maxQueueSize <= 0) {
            maxQueueSize = null;
        }

        CaseQueue updated = caseQueueService.updateQueue(
                queueId,
                request.getEnabled(),
                request.getAutoAssign(),
                maxQueueSize,
                targetRole,
                minPriority
        );

        long queuedNewCount = 0;
        try {
            queuedNewCount = caseRepository.countByQueueAndStatus(updated, CaseStatus.NEW);
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(CaseQueueOverview.from(updated, queuedNewCount));
    }

    /**
     * Trigger immediate processing (auto-assignment) for a queue
     */
    @PostMapping("/queues/{queueId}/process")
    public ResponseEntity<Map<String, Object>> processQueue(@PathVariable Long queueId) {
        caseQueueService.processQueue(queueId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // DTOs
    public static class EscalationRequest {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class CreateQueueRequest {
        private String queueName;
        private String targetRole;
        private String minPriority;
        private Integer maxQueueSize;
        private Boolean autoAssign;
        private Boolean enabled;

        public String getQueueName() { return queueName; }
        public void setQueueName(String queueName) { this.queueName = queueName; }

        public String getTargetRole() { return targetRole; }
        public void setTargetRole(String targetRole) { this.targetRole = targetRole; }

        public String getMinPriority() { return minPriority; }
        public void setMinPriority(String minPriority) { this.minPriority = minPriority; }

        public Integer getMaxQueueSize() { return maxQueueSize; }
        public void setMaxQueueSize(Integer maxQueueSize) { this.maxQueueSize = maxQueueSize; }

        public Boolean getAutoAssign() { return autoAssign; }
        public void setAutoAssign(Boolean autoAssign) { this.autoAssign = autoAssign; }

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    public static class UpdateQueueRequest {
        private Boolean enabled;
        private Boolean autoAssign;
        private Integer maxQueueSize;
        private String targetRole;
        private String minPriority;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public Boolean getAutoAssign() { return autoAssign; }
        public void setAutoAssign(Boolean autoAssign) { this.autoAssign = autoAssign; }

        public Integer getMaxQueueSize() { return maxQueueSize; }
        public void setMaxQueueSize(Integer maxQueueSize) { this.maxQueueSize = maxQueueSize; }

        public String getTargetRole() { return targetRole; }
        public void setTargetRole(String targetRole) { this.targetRole = targetRole; }

        public String getMinPriority() { return minPriority; }
        public void setMinPriority(String minPriority) { this.minPriority = minPriority; }
    }

    public static class CaseQueueOverview {
        private Long id;
        private String queueName;
        private String targetRole;
        private CasePriority minPriority;
        private Integer maxQueueSize;
        private Boolean autoAssign;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private long queuedNewCount;

        public static CaseQueueOverview from(CaseQueue queue, long queuedNewCount) {
            CaseQueueOverview dto = new CaseQueueOverview();
            dto.id = queue.getId();
            dto.queueName = queue.getQueueName();
            dto.targetRole = queue.getTargetRole();
            dto.minPriority = queue.getMinPriority();
            dto.maxQueueSize = queue.getMaxQueueSize();
            dto.autoAssign = Objects.requireNonNullElse(queue.getAutoAssign(), Boolean.FALSE);
            dto.enabled = Objects.requireNonNullElse(queue.getEnabled(), Boolean.TRUE);
            dto.createdAt = queue.getCreatedAt();
            dto.queuedNewCount = queuedNewCount;
            return dto;
        }

        public Long getId() { return id; }
        public String getQueueName() { return queueName; }
        public String getTargetRole() { return targetRole; }
        public CasePriority getMinPriority() { return minPriority; }
        public Integer getMaxQueueSize() { return maxQueueSize; }
        public Boolean getAutoAssign() { return autoAssign; }
        public Boolean getEnabled() { return enabled; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public long getQueuedNewCount() { return queuedNewCount; }
    }
}

