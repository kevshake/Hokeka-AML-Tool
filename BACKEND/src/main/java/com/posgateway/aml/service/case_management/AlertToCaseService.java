package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.CaseAlert;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.AlertDisposition;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.workflow.WorkflowAutomationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Alert → Case bridge service.
 *
 * <p>When an operator disposes an alert with a disposition that signals
 * escalation or confirmed suspicion ({@code ESCALATED}, {@code TRUE_POSITIVE_*},
 * or {@code MERGED_WITH_CASE}), this service auto-creates (or reuses) a
 * {@link ComplianceCase}, links the alert to it as a {@link CaseAlert},
 * sets the merchant/PSP context, and triggers auto-assignment.
 *
 * <p>This is the critical end-to-end wiring that ensures resolved alerts
 * evolve into actionable cases with proper escalation paths.
 */
@Service
public class AlertToCaseService {

    private static final Logger log = LoggerFactory.getLogger(AlertToCaseService.class);

    /** Dispositions that always trigger case creation. */
    private static final List<AlertDisposition> CASE_WORTHY = List.of(
            AlertDisposition.ESCALATED,
            AlertDisposition.TRUE_POSITIVE_SAR_FILED,
            AlertDisposition.TRUE_POSITIVE_BLOCKED,
            AlertDisposition.TRUE_POSITIVE_REPORTED,
            AlertDisposition.MERGED_WITH_CASE,
            AlertDisposition.PENDING_INFORMATION,
            AlertDisposition.ONGOING_MONITORING
    );

    private final AlertRepository alertRepository;
    private final ComplianceCaseRepository caseRepository;
    private final MerchantRepository merchantRepository;
    private final WorkflowAutomationService workflowService;
    private final CaseActivityService caseActivityService;
    private final CaseEscalationService caseEscalationService;

    public AlertToCaseService(AlertRepository alertRepository,
                               ComplianceCaseRepository caseRepository,
                               MerchantRepository merchantRepository,
                               WorkflowAutomationService workflowService,
                               CaseActivityService caseActivityService,
                               CaseEscalationService caseEscalationService) {
        this.alertRepository = alertRepository;
        this.caseRepository = caseRepository;
        this.merchantRepository = merchantRepository;
        this.workflowService = workflowService;
        this.caseActivityService = caseActivityService;
        this.caseEscalationService = caseEscalationService;
    }

    /**
     * Called after an alert is disposed. If the disposition warrants a case,
     * one is auto-created or an existing open case for the same merchant is
     * reused (alert batching).
     *
     * @return the created or reused case, or empty if no case was needed
     */
    @Transactional
    public Optional<ComplianceCase> resolveAlertToCase(Long alertId, AlertDisposition disposition,
                                                        String reason, User disposedBy) {
        if (!CASE_WORTHY.contains(disposition)) {
            return Optional.empty();
        }

        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        // Determine merchant and PSP
        Long merchantId = alert.getMerchantId();
        Long pspId = alert.getPspId() != null ? alert.getPspId() : resolvePspId(merchantId);

        // Determine priority from alert score
        CasePriority priority = resolvePriority(alert.getScore());

        // Build SLA deadline
        LocalDateTime slaDeadline = switch (priority) {
            case CRITICAL -> LocalDateTime.now().plusHours(4);
            case HIGH     -> LocalDateTime.now().plusHours(24);
            case MEDIUM   -> LocalDateTime.now().plusDays(3);
            case LOW      -> LocalDateTime.now().plusDays(7);
        };

        // Reuse existing open case for the same merchant (batch alerts into one case)
        ComplianceCase complianceCase = findExistingOpenCase(merchantId)
                .orElseGet(() -> {
                    ComplianceCase newCase = ComplianceCase.builder()
                            .caseReference("CASE-" + System.currentTimeMillis() + "-" +
                                    UUID.randomUUID().toString().substring(0, 4).toUpperCase())
                            .merchantId(merchantId)
                            .pspId(pspId)
                            .description("Auto-created from alert #" + alertId +
                                    " — " + disposition.getDescription())
                            .status(CaseStatus.NEW)
                            .priority(priority)
                            .slaDeadline(slaDeadline)
                            .daysOpen(0)
                            .build();
                    ComplianceCase saved = caseRepository.save(newCase);
                    log.info("Created compliance case {} from alert #{} (disposition={})",
                            saved.getCaseReference(), alertId, disposition);
                    return saved;
                });

        // Link alert to case via CaseAlert
        CaseAlert caseAlert = new CaseAlert(
                complianceCase,
                alert.getAction() != null ? alert.getAction() : "RULE",
                alert.getReason(),
                null,
                null,
                null,
                alert.getScore(),
                disposition.getDescription() + (reason != null ? ": " + reason : ""),
                null,
                alert.getCreatedAt() != null ? alert.getCreatedAt() : LocalDateTime.now()
        );
        complianceCase.getAlerts().add(caseAlert);

        // Update alert status
        alert.setStatus("case_created");
        alertRepository.save(alert);

        // Update case metadata
        int alertCount = complianceCase.getAlerts() != null ? complianceCase.getAlerts().size() : 0;
        complianceCase.setDescription(complianceCase.getDescription() +
                " [" + alertCount + " alert(s)]");

        ComplianceCase savedCase = caseRepository.save(complianceCase);

        // Log activity via case activity service (system action, no user)
        try {
            // find a system user for the activity log, or skip if none available.
            // W14-7 fix: this used to be `disposedBy != null ? disposedBy : null` -- a no-op
            // ternary that always just evaluated to disposedBy itself.
            User systemUser = disposedBy;
            if (systemUser != null) {
                caseActivityService.logActivity(
                        savedCase.getId(),
                        com.posgateway.aml.model.ActivityType.CASE_CREATED,
                        "Alert #" + alertId + " linked with disposition: " + disposition,
                        systemUser.getId(),
                        "alert_id=" + alertId + ";disposition=" + disposition,
                        alertId,
                        "ALERT"
                );
            }
        } catch (Exception e) {
            log.debug("Activity logging skipped for case {}: {}", savedCase.getCaseReference(), e.getMessage());
        }

        // Auto-assign via workload balancing (async)
        try {
            workflowService.autoAssignCase(savedCase);
        } catch (Exception e) {
            log.warn("Auto-assignment failed for case {}: {}", savedCase.getCaseReference(), e.getMessage());
        }

        // Check automatic escalation rules
        try {
            caseEscalationService.checkAutomaticEscalation(savedCase);
        } catch (Exception e) {
            log.debug("Auto-escalation check skipped for case {}: {}", savedCase.getCaseReference(), e.getMessage());
        }

        log.info("Alert #{} → Case {} (priority={}, sla={})",
                alertId, savedCase.getCaseReference(), priority, slaDeadline);
        return Optional.of(savedCase);
    }

    private CasePriority resolvePriority(Double score) {
        if (score == null) return CasePriority.MEDIUM;
        if (score >= 0.9) return CasePriority.CRITICAL;
        if (score >= 0.7) return CasePriority.HIGH;
        if (score >= 0.4) return CasePriority.MEDIUM;
        return CasePriority.LOW;
    }

    private Long resolvePspId(Long merchantId) {
        if (merchantId == null) return null;
        try {
            return merchantRepository.findById(merchantId)
                    .map(m -> m.getPsp() != null ? m.getPsp().getPspId() : null)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not resolve PSP for merchant {}: {}", merchantId, e.getMessage());
            return null;
        }
    }

    private Optional<ComplianceCase> findExistingOpenCase(Long merchantId) {
        if (merchantId == null) return Optional.empty();
        List<ComplianceCase> openCases = caseRepository.findByMerchantId(merchantId);
        return openCases.stream()
                .filter(c -> c.getStatus() != CaseStatus.CLOSED_CLEARED
                        && c.getStatus() != CaseStatus.CLOSED_SAR_FILED
                        && c.getStatus() != CaseStatus.CLOSED_BLOCKED
                        && c.getStatus() != CaseStatus.CLOSED_REJECTED)
                .findFirst();
    }
}
