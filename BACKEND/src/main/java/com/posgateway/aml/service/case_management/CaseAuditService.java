package com.posgateway.aml.service.case_management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.CaseAuditLog;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.repository.CaseAuditLogRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Service for Immutable Audit Logging and Case Replay.
 */
@Service
public class CaseAuditService {

    private final CaseAuditLogRepository auditRepository;
    private final ComplianceCaseRepository caseRepository;
    private final ObjectMapper objectMapper;
    private final CasePermissionService permissionService;

    @Autowired
    public CaseAuditService(CaseAuditLogRepository auditRepository,
            ComplianceCaseRepository caseRepository,
            ObjectMapper objectMapper,
            CasePermissionService permissionService) {
        this.auditRepository = auditRepository;
        this.caseRepository = caseRepository;
        this.objectMapper = objectMapper;
        this.permissionService = permissionService;
    }

    /**
     * Log a user action on a case.
     */
    @Transactional
    public void logAction(Long caseId, User user, String action, String details) {
        ComplianceCase kase = caseRepository.findById(caseId).orElse(null);
        String currentState = (kase != null) ? kase.getStatus().name() : "UNKNOWN";

        String snapshot = "";
        try {
            if (kase != null) {
                snapshot = "Status: " + kase.getStatus() + ", Assigned: "
                        + (kase.getAssignedTo() != null ? kase.getAssignedTo().getUsername() : "Unassigned");
            }
        } catch (Exception e) {
            snapshot = "Error serializing state";
        }

        CaseAuditLog log = new CaseAuditLog(caseId, action, details, user, snapshot, currentState);
        auditRepository.save(log);
    }

    /**
     * Replay the full history of a case.
     * Returns a chronological list of all actions.
     * SYSTEM INTERNAL USE ONLY.
     */
    public List<CaseAuditLog> replayCase(Long caseId) {
        return auditRepository.findByCaseIdOrderByTimestampAsc(caseId);
    }

    /**
     * Secure Replay for API.
     */
    public List<CaseAuditLog> replayCase(Long caseId, User user) throws IllegalAccessException {
        if (!permissionService.canView(caseId, user)) {
            throw new IllegalAccessException(
                    "User " + user.getUsername() + " does not have permission to view case " + caseId);
        }
        return replayCase(caseId);
    }

    /**
     * Export case history report for regulatory review.
     * SYSTEM INTERNAL USE ONLY (e.g. Archival Service).
     */
    public String exportCaseReport(Long caseId) {
        ComplianceCase kase = caseRepository.findById(caseId).orElse(null);
        if (kase == null) {
            return "{\"error\": \"Case not found\"}";
        }
        return generateReportJson(kase);
    }

    /**
     * Secure Export for API.
     */
    public String exportCaseReport(Long caseId, User user) throws IllegalAccessException {
        if (!permissionService.canView(caseId, user)) {
            throw new IllegalAccessException(
                    "User " + user.getUsername() + " does not have permission to export case " + caseId);
        }
        return exportCaseReport(caseId);
    }

    private String generateReportJson(ComplianceCase kase) {
        try {
            Map<String, Object> report = new java.util.HashMap<>();

            // 1. Case Metadata
            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("caseReference", kase.getCaseReference());
            metadata.put("status", kase.getStatus());
            metadata.put("priority", kase.getPriority());
            metadata.put("created", kase.getCreatedAt().toString());
            metadata.put("resolved", kase.getResolvedAt() != null ? kase.getResolvedAt().toString() : "N/A");
            metadata.put("resolution", kase.getResolution());
            report.put("metadata", metadata);

            // 2. Triggering Alerts (Reproducibility Data)
            List<Map<String, Object>> alerts = kase.getAlerts().stream().map(a -> {
                Map<String, Object> am = new java.util.HashMap<>();
                am.put("type", a.getAlertType());
                am.put("score", a.getScore());
                am.put("rule", a.getRuleName());
                am.put("modelVersion", a.getModelVersion()); // Critical for reproducibility
                am.put("ruleVersion", a.getRuleVersion()); // Critical for policy tracking
                am.put("triggeredAt", a.getTriggeredAt().toString());
                return am;
            }).toList();
            report.put("alerts", alerts);

            // 3. Immutable Audit Trail (Replay)
            List<CaseAuditLog> logs = replayCase(kase.getId());
            List<Map<String, Object>> auditHistory = logs.stream().map(l -> {
                Map<String, Object> lm = new java.util.HashMap<>();
                lm.put("timestamp", l.getTimestamp().toString());
                lm.put("user", l.getUser() != null ? l.getUser().getUsername() : "SYSTEM");
                lm.put("action", l.getAction());
                lm.put("details", l.getDetails());
                return lm;
            }).toList();
            report.put("auditHistory", auditHistory);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);

        } catch (Exception e) {
            return "{\"error\": \"Failed to generate report: " + e.getMessage() + "\"}";
        }
    }
}
