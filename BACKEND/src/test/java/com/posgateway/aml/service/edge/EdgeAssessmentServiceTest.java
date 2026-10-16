package com.posgateway.aml.service.edge;

import com.posgateway.aml.dto.edge.EdgeAssessmentIngestRequest;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgeAssessmentServiceTest {

    @Mock
    private AssessmentRepository assessments;
    @Mock
    private FindingRepository findings;
    @Mock
    private TransactionRepository transactions;

    private EdgeAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new EdgeAssessmentService(assessments, findings, transactions);
    }

    /** Adopt returns empty when the ingest request did not include an edge assessment id. */
    @Test
    void adoptEmptyWhenNoEdgeId() {
        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(1L);
        assertTrue(service.adopt(txn, null).isEmpty());
        assertTrue(service.adopt(txn, "  ").isEmpty());
    }

    /** Recording an edge envelope persists decision and marks duplicate replays. */
    @Test
    void recordPersistsAssessment() {
        EdgeNode node = new EdgeNode();
        node.setEdgeId("edge-1");
        node.setPspId(10L);
        EdgeAssessmentIngestRequest req = new EdgeAssessmentIngestRequest(
                "ea-123", "hash-1", List.of(7L), List.of(), "HOLD", 12, null, 42L);

        when(assessments.findByEdgeAssessmentId("ea-123")).thenReturn(Optional.empty());
        when(assessments.saveAndFlush(any())).thenAnswer(inv -> {
            Assessment a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });
        var response = service.record(node, req);
        assertFalse(response.duplicate());
        assertEquals("ea-123", response.edgeAssessmentId());
        verify(assessments).save(any(Assessment.class));
    }

    /** Adopt links the transaction to an existing edge assessment row. */
    @Test
    void adoptLinksTransaction() {
        Assessment existing = new Assessment();
        existing.setId(UUID.randomUUID());
        existing.setEdgeAssessmentId("ea-99");
        existing.setPspId(5L);
        existing.setDecision("ALLOW");

        TransactionEntity txn = new TransactionEntity();
        txn.setTxnId(99L);
        txn.setPspId(5L);

        when(assessments.findByEdgeAssessmentId("ea-99")).thenReturn(Optional.of(existing));
        when(findings.findByAssessmentIdOrderByCreatedAtAsc(existing.getId())).thenReturn(List.of());

        var result = service.adopt(txn, "ea-99");
        assertTrue(result.isPresent());
        assertEquals("ALLOW", result.get().getAction());

        ArgumentCaptor<Assessment> saved = ArgumentCaptor.forClass(Assessment.class);
        verify(assessments).save(saved.capture());
        assertEquals(99L, saved.getValue().getTxnId());
    }
}
