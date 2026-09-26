package com.posgateway.aml.service.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.jev.JevDecisionAudit;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JevAuditAccessServiceDisclosureTest {

    @Mock private JevAuditService auditService;
    @Mock private PspIsolationService pspIsolationService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private ComplianceCaseRepository complianceCaseRepository;
    @Mock private MerchantRepository merchantRepository;

    private JevAuditAccessService service;

    @BeforeEach
    void setUp() {
        service = new JevAuditAccessService(
                auditService,
                new ObjectMapper().findAndRegisterModules(),
                pspIsolationService,
                transactionRepository,
                alertRepository,
                complianceCaseRepository,
                merchantRepository);
    }

    @Test
    void tenantAuditReadOmitsVendorAndCostFields() {
        Alert alert = new Alert();
        alert.setAlertId(1L);
        alert.setPspId(7L);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(false);
        lenient().doNothing().when(pspIsolationService).validatePspAccess(7L);
        when(auditService.forAlert(1L)).thenReturn(List.of(sampleAudit()));

        List<Map<String, Object>> rows = service.forAlert(1L);

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertNull(row.get("modelId"));
        assertNull(row.get("promptVersion"));
        assertNull(row.get("engineCode"));
        assertNull(row.get("fallbackReason"));
        assertNotNull(row.get("recommendation"));
        JevAiDisclosureSanitizer.assertPspSafeMap("tenant audit", row);
    }

    @Test
    void operatorAuditReadRetainsFullDetail() {
        Alert alert = new Alert();
        alert.setAlertId(1L);
        alert.setPspId(7L);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(true);
        when(auditService.forAlert(1L)).thenReturn(List.of(sampleAudit()));

        Map<String, Object> row = service.forAlert(1L).get(0);

        assertEquals("anthropic/claude-3.5-sonnet", row.get("modelId"));
        assertEquals("v2", row.get("promptVersion"));
        assertNotNull(row.get("inputTokens"));
        assertNotNull(row.get("estimatedCostUsd"));
    }

    private static JevDecisionAudit sampleAudit() {
        JevDecisionAudit audit = new JevDecisionAudit();
        audit.setId(99L);
        audit.setPspId(7L);
        audit.setEngineCode("TRANSACTION_RISK");
        audit.setModelId("anthropic/claude-3.5-sonnet");
        audit.setPromptVersion("v2");
        audit.setRecommendation("REVIEW");
        audit.setConfidence(0.82);
        audit.setReasons(List.of("borderline velocity"));
        audit.setFallbackReason("Laya error: TimeoutException");
        audit.setInputTokens(120);
        audit.setOutputTokens(45);
        audit.setEstimatedCostUsd(BigDecimal.valueOf(0.002));
        audit.setCreatedAt(Instant.parse("2026-01-01T12:00:00Z"));
        audit.setAiApplied(false);
        return audit;
    }
}
