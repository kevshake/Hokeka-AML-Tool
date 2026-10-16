package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.AssessmentTriggerType;
import com.posgateway.aml.entity.compliance.AlertTransactionLink;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.compliance.AlertTransactionLinkRepository;
import com.posgateway.aml.repository.compliance.SarAlertLinkRepository;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** R8: alerts and their links reference the transaction by ID only, never a card hash. */
class ComplianceEntityLinkServiceTest {

    private AlertRepository alertRepository;
    private AlertTransactionLinkRepository alertTransactionLinkRepository;
    private CaseTransactionRepository caseTransactionRepository;
    private AssessmentRepository assessments;
    private ComplianceEntityLinkService service;

    /** Real resolver over mocked repositories, so the legacy reference rule is exercised too. */
    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        alertTransactionLinkRepository = mock(AlertTransactionLinkRepository.class);
        caseTransactionRepository = mock(CaseTransactionRepository.class);
        assessments = mock(AssessmentRepository.class);
        TransactionReferenceResolver resolver = new TransactionReferenceResolver(assessments,
                mock(TransactionRepository.class));
        service = new ComplianceEntityLinkService(alertRepository, alertTransactionLinkRepository,
                mock(SarAlertLinkRepository.class), caseTransactionRepository, resolver);
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registerNewFraudAlertStampsReferenceAndLinksWithoutPanHash() {
        Alert alert = new Alert();
        alert.setAlertId(10L);
        alert.setPspId(3L);
        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(99L);
        txn.setPspId(3L);
        txn.setPanHash("a".repeat(64));
        txn.setClientReference("ORD-77");
        Assessment cloudAssessment = assessment(UUID.randomUUID(), null, 3L, 99L, Instant.parse("2026-01-01T00:00:00Z"));
        UUID edgeId = UUID.randomUUID();
        Assessment adopted = assessment(UUID.randomUUID(), edgeId, 3L, 99L, Instant.parse("2026-01-02T00:00:00Z"));
        adopted.setSiteId("site-a");
        when(assessments.findByPspIdAndTxnIdAndTriggerTypeOrderByCreatedAtAscIdAsc(3L, 99L, AssessmentTriggerType.TXN))
                .thenReturn(List.of(cloudAssessment, adopted));
        when(alertTransactionLinkRepository.findByAlertIdOrderByPrimaryLinkDescIdDesc(10L)).thenReturn(List.of());

        service.registerNewFraudAlert(alert, txn);

        // The Edge-adopted assessment wins over the older cloud one; txnRef is the PSP reference.
        assertEquals(edgeId, alert.getEdgeAssessmentId());
        assertEquals("ORD-77", alert.getTxnRef());
        assertEquals("site-a", alert.getSiteId());
        assertEquals(99L, alert.getTxnId());
        ArgumentCaptor<AlertTransactionLink> captor = ArgumentCaptor.forClass(AlertTransactionLink.class);
        verify(alertTransactionLinkRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getAlertId());
        assertEquals(99L, captor.getValue().getTxnId());
        assertEquals(edgeId, captor.getValue().getEdgeAssessmentId());
        assertEquals("ORD-77", captor.getValue().getTxnRef());
        assertTrue(captor.getValue().isPrimaryLink());
    }

    @Test
    void legacyTransactionWithoutAssessmentKeepsLegacyIdAndDropsCardShapedReference() {
        Alert alert = new Alert();
        alert.setAlertId(11L);
        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(100L);
        txn.setPspId(3L);
        txn.setClientReference("4111111111111111");
        when(assessments.findByPspIdAndTxnIdAndTriggerTypeOrderByCreatedAtAscIdAsc(3L, 100L, AssessmentTriggerType.TXN))
                .thenReturn(List.of());
        when(alertTransactionLinkRepository.findByAlertIdOrderByPrimaryLinkDescIdDesc(11L)).thenReturn(List.of());

        service.registerNewFraudAlert(alert, txn);

        assertNull(alert.getEdgeAssessmentId());
        assertNull(alert.getTxnRef(), "a card-shaped client reference is never a txnRef");
        assertEquals(100L, alert.getTxnId());
        verify(alertTransactionLinkRepository).save(any(AlertTransactionLink.class));
    }

    @Test
    void linkAlertToTransactionIsIdempotentPerTransaction() {
        UUID edgeId = UUID.randomUUID();
        AlertTransactionLink existing = new AlertTransactionLink();
        existing.setAlertId(12L);
        existing.setEdgeAssessmentId(edgeId);
        when(alertTransactionLinkRepository.findByAlertIdOrderByPrimaryLinkDescIdDesc(12L)).thenReturn(List.of(existing));

        service.linkAlertToTransaction(12L, TransactionReference.ofDecisionRecord(edgeId, "T-1", "s1"), true);

        verify(alertTransactionLinkRepository, never()).save(any());
    }

    @Test
    void caseTransactionReferencesAreTenantScoped() {
        ComplianceCase c = new ComplianceCase();
        c.setId(5L);
        c.setPspId(3L);
        UUID edgeId = UUID.randomUUID();
        when(caseTransactionRepository.findByComplianceCase_Id(5L)).thenReturn(List.of(
                new CaseTransaction(c, TransactionReference.ofDecisionRecord(edgeId, "T-9", null), "PRIMARY", null)));

        List<TransactionReference> own = service.caseTransactionReferences(c, 3L);
        assertEquals(1, own.size());
        assertEquals(edgeId, own.get(0).edgeAssessmentId());
        assertTrue(service.caseTransactionReferences(c, 4L).isEmpty(), "another PSP never sees the case's refs");
    }

    /** A TXN assessment row. */
    private static Assessment assessment(UUID id, UUID edgeId, Long pspId, Long txnId, Instant createdAt) {
        Assessment a = new Assessment();
        a.setId(id);
        a.setEdgeAssessmentId(edgeId);
        a.setPspId(pspId);
        a.setTxnId(txnId);
        a.setTriggerType(AssessmentTriggerType.TXN);
        a.setTriggerRef(String.valueOf(txnId));
        a.setCreatedAt(createdAt);
        return a;
    }
}
