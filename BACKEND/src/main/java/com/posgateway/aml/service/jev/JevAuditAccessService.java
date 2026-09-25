package com.posgateway.aml.service.jev;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.jev.JevDecisionAudit;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Tenant-scoped JEV audit reads for Console verdict panels. Platform operators receive full
 * audit rows; PSP/bank users receive redacted advisory fields for their own tenant only.
 */
@Service
public class JevAuditAccessService {

    private final JevAuditService auditService;
    private final PspIsolationService pspIsolationService;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final ComplianceCaseRepository complianceCaseRepository;
    private final MerchantRepository merchantRepository;

    public JevAuditAccessService(JevAuditService auditService,
                                 PspIsolationService pspIsolationService,
                                 TransactionRepository transactionRepository,
                                 AlertRepository alertRepository,
                                 ComplianceCaseRepository complianceCaseRepository,
                                 MerchantRepository merchantRepository) {
        this.auditService = auditService;
        this.pspIsolationService = pspIsolationService;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.complianceCaseRepository = complianceCaseRepository;
        this.merchantRepository = merchantRepository;
    }

    public List<Map<String, Object>> forTransaction(Long transactionId) {
        TransactionEntity txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        pspIsolationService.validateTransactionAccess(txn);
        return mapScoped(auditService.forTransaction(transactionId));
    }

    public List<Map<String, Object>> forAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (alert.getPspId() != null) {
            pspIsolationService.validatePspAccess(alert.getPspId());
        }
        return mapScoped(auditService.forAlert(alertId));
    }

    public List<Map<String, Object>> forCase(Long caseId) {
        ComplianceCase complianceCase = complianceCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        pspIsolationService.validateCaseAccess(complianceCase);
        return mapScoped(auditService.forCase(caseId));
    }

    public List<Map<String, Object>> forMerchant(Long merchantId, String engine) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        pspIsolationService.validateMerchantAccess(merchant);
        return mapScoped(auditService.forMerchant(merchantId, engine));
    }

    public List<Map<String, Object>> byId(Long auditId) {
        JevDecisionAudit audit = auditService.byId(auditId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertAuditTenantAccess(audit);
        return List.of(toResponseDto(audit));
    }

    public List<Map<String, Object>> forScreeningHit(String screeningHitId) {
        List<JevDecisionAudit> audits = auditService.forScreeningHit(screeningHitId).stream()
                .filter(this::callerMayViewAudit)
                .toList();
        return audits.stream().map(this::toResponseDto).toList();
    }

    private List<Map<String, Object>> mapScoped(List<JevDecisionAudit> audits) {
        return audits.stream()
                .filter(this::callerMayViewAudit)
                .map(this::toResponseDto)
                .toList();
    }

    private boolean callerMayViewAudit(JevDecisionAudit audit) {
        if (pspIsolationService.isPlatformAdministrator()) {
            return true;
        }
        if (audit.getPspId() == null) {
            return false;
        }
        try {
            pspIsolationService.validatePspAccess(audit.getPspId());
            return true;
        } catch (SecurityException ex) {
            return false;
        }
    }

    private void assertAuditTenantAccess(JevDecisionAudit audit) {
        if (!callerMayViewAudit(audit)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access JEV audit outside your tenant");
        }
    }

    private Map<String, Object> toResponseDto(JevDecisionAudit audit) {
        if (pspIsolationService.isPlatformAdministrator()) {
            return auditService.toDto(audit);
        }
        return toTenantAdvisoryDto(audit);
    }

    /** Advisory fields only — no model id, spend, tokens, or raw model payloads. */
    static Map<String, Object> toTenantAdvisoryDto(JevDecisionAudit audit) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", audit.getId());
        dto.put("engineCode", audit.getEngineCode());
        dto.put("recommendation", audit.getRecommendation());
        dto.put("riskScore", audit.getRiskScore());
        dto.put("confidence", audit.getConfidence());
        dto.put("reasons", audit.getReasons());
        dto.put("citedSignals", audit.getCitedSignals());
        dto.put("fallbackReason", audit.getFallbackReason());
        dto.put("aiApplied", audit.isAiApplied());
        dto.put("baselineDecision", audit.getBaselineDecision());
        dto.put("finalDecision", audit.getFinalDecision());
        dto.put("createdAt", audit.getCreatedAt());
        dto.put("advisoryOnly", true);
        return dto;
    }
}
