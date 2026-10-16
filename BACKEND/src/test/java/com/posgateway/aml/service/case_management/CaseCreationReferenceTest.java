package com.posgateway.aml.service.case_management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** R8: cases keep the triggering transaction's ID reference, never a snapshot of the transaction. */
class CaseCreationReferenceTest {

    private ComplianceCaseRepository cases;
    private CaseEnrichmentService enrichment;
    private TransactionReferenceResolver resolver;
    private CaseCreationService service;

    /** Mocked repositories; the case is saved as given. */
    @BeforeEach
    void setUp() {
        cases = mock(ComplianceCaseRepository.class);
        when(cases.findByMerchantId(any())).thenReturn(List.of());
        when(cases.save(any(ComplianceCase.class))).thenAnswer(i -> {
            ComplianceCase c = i.getArgument(0);
            c.setId(31L);
            return c;
        });
        RuleDefinitionRepository rules = mock(RuleDefinitionRepository.class);
        when(rules.findFirstByNameOrderByIdAsc(any())).thenReturn(Optional.empty());
        enrichment = mock(CaseEnrichmentService.class);
        resolver = mock(TransactionReferenceResolver.class);
        service = new CaseCreationService(cases, new ObjectMapper(), null, null, enrichment, rules, resolver);
    }

    @Test
    void legacyRuleCaseStoresReferenceDocumentAndLinksByReference() {
        TransactionEntity tx = new TransactionEntity();
        tx.setTxnId(88L);
        tx.setPspId(4L);
        tx.setMerchantId("6");
        tx.setAmountCents(990000L);
        tx.setCurrency("KES");
        tx.setPanHash("b".repeat(64));
        tx.setIpAddress("10.1.1.1");
        UUID edgeId = UUID.randomUUID();
        TransactionReference ref = new TransactionReference(88L, edgeId, "ORD-88", null);
        when(resolver.forLegacyTransaction(tx)).thenReturn(ref);

        service.triggerCaseFromRule(tx, "R-1", "velocity");

        ArgumentCaptor<ComplianceCase> saved = ArgumentCaptor.forClass(ComplianceCase.class);
        verify(cases, atLeastOnce()).save(saved.capture());
        String raw = saved.getValue().getAlerts().get(0).getRawData();
        assertTrue(raw.contains(edgeId.toString()), raw);
        assertTrue(raw.contains("ORD-88"), raw);
        assertFalse(raw.contains("990000"), "no amount in the case alert: " + raw);
        assertFalse(raw.contains("b".repeat(64)), "no card hash in the case alert: " + raw);
        assertFalse(raw.contains("10.1.1.1"), "no IP in the case alert: " + raw);
        assertEquals(4L, saved.getValue().getPspId());
        verify(enrichment).enrichWithTransaction(any(ComplianceCase.class), eq(ref), eq("TRIGGERING_TRANSACTION"));
    }

    @Test
    void edgeDecisionCaseNeedsTheDecisionRecordId() {
        assertThrows(IllegalArgumentException.class, () -> service.triggerCaseFromEdgeDecision(6L, 4L,
                "RULE_VIOLATION", "r", null, null, 0.5, "d", TransactionReference.ofLegacyTransaction(1L), 9L));

        UUID edgeId = UUID.randomUUID();
        TransactionReference ref = TransactionReference.ofDecisionRecord(edgeId, "AUTH-3", "site-x");
        ComplianceCase c = service.triggerCaseFromEdgeDecision(6L, 4L, "RULE_VIOLATION", "r", null, null, 0.5, "d",
                ref, 9L);
        String raw = c.getAlerts().get(c.getAlerts().size() - 1).getRawData();
        assertTrue(raw.contains("\"source\":\"EDGE_DECISION\""), raw);
        assertTrue(raw.contains("\"siteId\":\"site-x\""), raw);
        assertTrue(raw.contains("\"alertId\":9"), raw);
        verify(enrichment).enrichWithTransaction(any(ComplianceCase.class), eq(ref), eq("TRIGGERING_TRANSACTION"));
    }
}
