package com.posgateway.aml.service.ai.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.dto.ai.AiOperatorAuditDto;
import com.posgateway.aml.dto.ai.AiTenantAdvisoryDto;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.ai.AiDecisionAudit;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
/**
 * Tenant-scoped JEV audit reads for Console verdict panels. Platform operators receive full
 * audit rows; PSP/bank users receive redacted advisory fields for their own tenant only.
 */
@Service
public class AiAuditAccessService {

    private final AiAuditService auditService;
    private final ObjectMapper objectMapper;
    private final PspIsolationService pspIsolationService;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final ComplianceCaseRepository complianceCaseRepository;
    private final MerchantRepository merchantRepository;

    public AiAuditAccessService(AiAuditService auditService,
                                 ObjectMapper objectMapper,
                                 PspIsolationService pspIsolationService,
                                 TransactionRepository transactionRepository,
                                 AlertRepository alertRepository,
                                 ComplianceCaseRepository complianceCaseRepository,
                                 MerchantRepository merchantRepository) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
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
        AiDecisionAudit audit = auditService.byId(auditId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertAuditTenantAccess(audit);
        return List.of(toResponseDto(audit));
    }

    public List<Map<String, Object>> forScreeningHit(String screeningHitId) {
        List<AiDecisionAudit> audits = auditService.forScreeningHit(screeningHitId).stream()
                .filter(this::callerMayViewAudit)
                .toList();
        return audits.stream().map(this::toResponseDto).toList();
    }

    private List<Map<String, Object>> mapScoped(List<AiDecisionAudit> audits) {
        return audits.stream()
                .filter(this::callerMayViewAudit)
                .map(this::toResponseDto)
                .toList();
    }

    private boolean callerMayViewAudit(AiDecisionAudit audit) {
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

    private void assertAuditTenantAccess(AiDecisionAudit audit) {
        if (!callerMayViewAudit(audit)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access AI audit outside your tenant");
        }
    }

    private Map<String, Object> toResponseDto(AiDecisionAudit audit) {
        if (pspIsolationService.isPlatformAdministrator()) {
            return AiOperatorAuditDto.from(audit, objectMapper);
        }
        return AiTenantAdvisoryDto.from(audit).toMap();
    }
}
