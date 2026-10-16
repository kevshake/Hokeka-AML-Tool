package com.posgateway.aml.service.edge.records;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.contracts.ContractFixtures;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.Finding;
import com.posgateway.aml.entity.assessment.FindingPhase;
import com.posgateway.aml.entity.assessment.FindingSourceType;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import com.posgateway.aml.service.residency.CanonicalJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Storage of one decision record v2: class-D mapping only, idempotency, findings, rollup, workflow. */
class DecisionRecordStoreTest {

    private AssessmentRepository assessments;
    private FindingRepository findings;
    private DecisionStatsHourlyStore stats;
    private DecisionRecordWorkflowService workflow;
    private DecisionRecordStore store;
    private EdgeNode node;

    @BeforeEach
    void setUp() {
        assessments = mock(AssessmentRepository.class);
        findings = mock(FindingRepository.class);
        stats = mock(DecisionStatsHourlyStore.class);
        workflow = mock(DecisionRecordWorkflowService.class);
        when(assessments.saveAndFlush(any(Assessment.class))).thenAnswer(i -> i.getArgument(0));
        when(assessments.findByEdgeAssessmentId(any())).thenReturn(Optional.empty());
        store = new DecisionRecordStore(assessments, findings, stats, workflow);
        node = new EdgeNode();
        node.setPspId(12L);
        node.setEdgeId("acme-eu-1");
    }

    @Test
    void sanctionsRecordIsStoredWithDecisionFieldsOnly() {
        JsonNode record = ContractFixtures.fixture("decision-record-v2--sanctions-hit.json");
        String digest = CanonicalJson.sha256Hex(record);

        assertEquals(DecisionRecordStore.Outcome.ACCEPTED, store.store(node, record, digest));

        ArgumentCaptor<Assessment> saved = ArgumentCaptor.forClass(Assessment.class);
        verify(assessments).saveAndFlush(saved.capture());
        Assessment row = saved.getValue();
        assertEquals(UUID.fromString("c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f"), row.getEdgeAssessmentId());
        assertEquals(12L, row.getPspId());
        assertEquals(2, row.getRecordVersion());
        assertEquals("AUTH-2026-10-16-001207", row.getTxnRef());
        assertEquals(Instant.parse("2026-10-16T10:02:51Z"), row.getEvaluatedAt());
        assertEquals("BLOCK", row.getDecision());
        assertEquals(100, row.getScore());
        assertEquals(List.of(9001L), row.getTriggeredRuleIds());
        assertEquals(List.of("ofac-sdn:36216", "un:QDi.001"), row.getWatchlistEntryIds());
        assertEquals(List.of("SANCTIONS_COUNTERPARTY", "CASH_THRESHOLD"), row.getTypologyCodes());
        assertTrue(row.getCtrRequired());
        assertTrue(row.getSanctionsHit());
        assertEquals("mrc_00421", row.getMerchantId());
        assertEquals(37L, row.getBundleVersion());
        assertEquals("java", row.getEvaluator());
        assertEquals(digest, row.getRecordDigest());
        assertNull(row.getTxnId(), "no transactions row is linked");
        // versions JSON carries provenance only, never a feature snapshot
        assertFalse(row.getVersions().containsKey("featureSnapshot"));

        ArgumentCaptor<Finding> finding = ArgumentCaptor.forClass(Finding.class);
        verify(findings, times(3)).save(finding.capture());
        assertEquals(FindingSourceType.RULE, finding.getAllValues().get(0).getSourceType());
        assertEquals("9001", finding.getAllValues().get(0).getSourceId());
        assertEquals("91002", finding.getAllValues().get(0).getSourceVersion());
        assertEquals(FindingSourceType.SCREENING, finding.getAllValues().get(1).getSourceType());
        assertEquals(FindingPhase.EDGE_SYNC, finding.getAllValues().get(2).getPhase());

        verify(stats).add(12L, Instant.parse("2026-10-16T10:02:51Z"), "acme-eu", "mrc_00421", "BLOCK",
                false, true, true, 100L, 11L);
        verify(workflow).raise(row);
    }

    @Test
    void identicalResendIsADuplicateAndDifferentContentIsAConflict() {
        JsonNode record = ContractFixtures.fixture("decision-record-v2--alert.json");
        String digest = CanonicalJson.sha256Hex(record);
        Assessment existing = new Assessment();
        existing.setId(UUID.randomUUID());
        existing.setPspId(12L);
        existing.setRecordVersion(2);
        existing.setRecordDigest(digest);
        when(assessments.findByEdgeAssessmentId(any())).thenReturn(Optional.of(existing));

        assertEquals(DecisionRecordStore.Outcome.DUPLICATE, store.store(node, record, digest));
        assertEquals(DecisionRecordStore.Outcome.CONFLICT, store.store(node, record, "f".repeat(64)));
        verify(assessments, never()).saveAndFlush(any());
        verify(workflow, never()).raise(any());
    }

    @Test
    void anIdOwnedByAnotherPspIsAConflict() {
        Assessment existing = new Assessment();
        existing.setPspId(99L);
        when(assessments.findByEdgeAssessmentId(any())).thenReturn(Optional.of(existing));
        JsonNode record = ContractFixtures.fixture("decision-record-v2--alert.json");
        assertEquals(DecisionRecordStore.Outcome.CONFLICT, store.store(node, record, CanonicalJson.sha256Hex(record)));
    }

    @Test
    void theDualPostAdoptShellIsCompletedWithTheV2Fields() {
        Assessment shell = new Assessment();
        shell.setId(UUID.randomUUID());
        shell.setPspId(12L);
        shell.setTxnId(555L);
        shell.setEdgeAssessmentId(UUID.fromString("3f0c9a2e-7d41-4b8e-9a51-0c2f6b1d7e44"));
        when(assessments.findByEdgeAssessmentId(any())).thenReturn(Optional.of(shell));
        JsonNode record = ContractFixtures.fixture("decision-record-v2--alert.json");

        assertEquals(DecisionRecordStore.Outcome.ACCEPTED, store.store(node, record, CanonicalJson.sha256Hex(record)));
        assertEquals(2, shell.getRecordVersion());
        assertEquals("ALERT", shell.getDecision());
        assertEquals(555L, shell.getTxnId(), "the legacy link is kept until R8 detaches it");
    }

    @Test
    void aV1DecisionThatDiffersIsAConflict() {
        Assessment v1 = new Assessment();
        v1.setPspId(12L);
        v1.setDecision("ALLOW");
        when(assessments.findByEdgeAssessmentId(any())).thenReturn(Optional.of(v1));
        JsonNode record = ContractFixtures.fixture("decision-record-v2--alert.json");
        assertEquals(DecisionRecordStore.Outcome.CONFLICT, store.store(node, record, CanonicalJson.sha256Hex(record)));
    }

    @Test
    void failClosedRecordCountsAsFailClosedInTheRollup() {
        JsonNode record = ContractFixtures.fixture("decision-record-v2--fail-closed.json");
        node.setEdgeId("acme-eu-2");
        assertEquals(DecisionRecordStore.Outcome.ACCEPTED, store.store(node, record, CanonicalJson.sha256Hex(record)));
        verify(stats).add(eq(12L), any(), eq("acme-eu"), eq(null), eq("HOLD"), eq(true), anyBoolean(), anyBoolean(),
                anyLong(), anyLong());
        verify(findings, never()).save(any());
    }

    @Test
    void storedVersionsMapHasNoTransactionData() {
        JsonNode record = ContractFixtures.fixture("decision-record-v2--alert.json");
        store.store(node, record, CanonicalJson.sha256Hex(record));
        ArgumentCaptor<Assessment> saved = ArgumentCaptor.forClass(Assessment.class);
        verify(assessments).saveAndFlush(saved.capture());
        Map<String, Object> versions = saved.getValue().getVersions();
        assertEquals(Set.of("recordVersion", "edgeId", "bundleVersion", "bundleManifestHash", "ruleVersionIds"),
                versions.keySet());
        verify(stats).add(anyLong(), any(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyLong(), anyLong());
    }
}
