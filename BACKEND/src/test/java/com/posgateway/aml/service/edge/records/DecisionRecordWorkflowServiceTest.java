package com.posgateway.aml.service.edge.records;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.alert.AlertFindingLink;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.Finding;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceDeadlineRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.alert.AlertFindingLinkRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.ConfigService;
import com.posgateway.aml.service.case_management.CaseCreationService;
import com.posgateway.aml.service.kafka.KafkaOutboxService;
import com.posgateway.aml.service.psp.WebhookOutboxService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alerts, cases, CTR deadlines and ID-only events raised from decision records (contract 4.4),
 * referencing the transaction by edgeAssessmentId / txnRef only.
 */
class DecisionRecordWorkflowServiceTest {

    private static final UUID EDGE_ID = UUID.fromString("c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f");

    private AlertRepository alerts;
    private CaseCreationService cases;
    private ConfigService config;
    private KafkaOutboxService kafka;
    private WebhookOutboxService webhooks;
    private ComplianceDeadlineRepository deadlines;
    private AlertFindingLinkRepository links;
    private FindingRepository findings;
    private DecisionRecordWorkflowService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        alerts = mock(AlertRepository.class);
        cases = mock(CaseCreationService.class);
        config = mock(ConfigService.class);
        kafka = mock(KafkaOutboxService.class);
        webhooks = mock(WebhookOutboxService.class);
        deadlines = mock(ComplianceDeadlineRepository.class);
        links = mock(AlertFindingLinkRepository.class);
        findings = mock(FindingRepository.class);
        RuleDefinitionRepository rules = mock(RuleDefinitionRepository.class);
        PspRepository psps = mock(PspRepository.class);
        MerchantRepository merchants = mock(MerchantRepository.class);

        when(alerts.save(any(Alert.class))).thenAnswer(i -> {
            Alert a = i.getArgument(0);
            if (a.getAlertId() == null) {
                a.setAlertId(501L);
            }
            return a;
        });
        when(config.isRuleAutoCreateCasesEnabled()).thenReturn(true);
        RuleDefinition sanctionsRule = new RuleDefinition();
        sanctionsRule.setId(9001L);
        sanctionsRule.setName("Sanctioned counterparty");
        when(rules.findAllById(any())).thenReturn(List.of(sanctionsRule));
        Psp psp = new Psp();
        psp.setPspId(12L);
        psp.setCountry("KEN");
        when(psps.findById(12L)).thenReturn(Optional.of(psp));
        Merchant merchant = new Merchant();
        merchant.setMerchantId(421L);
        merchant.setPsp(psp);
        when(merchants.findByMerchantId(421L)).thenReturn(Optional.of(merchant));
        ComplianceCase c = new ComplianceCase();
        c.setId(77L);
        when(cases.triggerCaseFromEdgeDecision(any(), anyLong(), anyString(), any(), any(), any(), any(), anyString(),
                anyString(), any(), anyLong())).thenReturn(c);
        when(deadlines.findBySourceTypeAndSourceId(anyString(), anyLong())).thenReturn(Optional.empty());
        when(deadlines.save(any(ComplianceDeadline.class))).thenAnswer(i -> {
            ComplianceDeadline d = i.getArgument(0);
            d.setId(900L);
            return d;
        });
        Finding f = new Finding();
        f.setId(UUID.randomUUID());
        when(findings.findByAssessmentIdOrderByCreatedAtAsc(any())).thenReturn(List.of(f));

        service = new DecisionRecordWorkflowService(alerts, links, findings, rules, cases, config, kafka, webhooks,
                deadlines, psps, merchants, mapper);
    }

    @Test
    void sanctionsBlockRaisesAlertSanctionsCaseRuleCaseAndCtrDeadline() throws Exception {
        Assessment record = record("BLOCK", 100, List.of(9001L), true, true, "421");

        DecisionRecordWorkflowService.Outcome outcome = service.raise(record);

        ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
        verify(alerts, org.mockito.Mockito.atLeastOnce()).save(alert.capture());
        Alert saved = alert.getValue();
        assertNull(saved.getTxnId(), "no TransactionEntity reference");
        assertEquals("EDGE_DECISION", saved.getSourceType());
        assertEquals(EDGE_ID.toString(), saved.getSourceReference());
        assertEquals("BLOCK", saved.getAction());
        assertEquals("CRITICAL", saved.getSeverity());
        assertEquals(421L, saved.getMerchantId());
        assertTrue(saved.isCtrRequired());
        assertTrue(saved.getReason().contains("Sanctioned counterparty (9001)"), saved.getReason());
        assertEquals(77L, saved.getComplianceCaseId());
        assertEquals("[\"Sanctioned counterparty\"]", saved.getTriggeredRules());

        verify(cases).triggerCaseFromEdgeDecision(eq(421L), eq(12L), eq("SANCTIONS_HIT"), any(), isNull(), isNull(),
                eq(1.0), anyString(), eq(EDGE_ID.toString()), eq("AUTH-1"), eq(501L));
        verify(cases).triggerCaseFromEdgeDecision(eq(421L), eq(12L), eq("RULE_VIOLATION"), eq("Sanctioned counterparty"),
                eq("9001"), any(), eq(100.0), anyString(), eq(EDGE_ID.toString()), eq("AUTH-1"), eq(501L));
        assertEquals(List.of(77L), outcome.caseIds());

        ArgumentCaptor<ComplianceDeadline> deadline = ArgumentCaptor.forClass(ComplianceDeadline.class);
        verify(deadlines).save(deadline.capture());
        assertEquals("CTR_FILING", deadline.getValue().getDeadlineType());
        assertEquals("ALERT", deadline.getValue().getSourceType());
        assertEquals(501L, deadline.getValue().getSourceId());
        assertEquals(900L, outcome.ctrDeadlineId());

        verify(links).save(any(AlertFindingLink.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void webhookCarriesIdsAndTheDecisionOnly() {
        service.raise(record("ALERT", 45, List.of(9001L), false, false, "421"));
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(webhooks).enqueueForPsp(eq(12L), eq("RISK_ALERT"), payload.capture(), eq("webhook.risk_alert:501"));
        assertEquals(Set.of("alertId", "edgeAssessmentId", "txnRef", "decision", "pspId", "merchantId", "caseId",
                "timestamp"), payload.getValue().keySet());
        verify(kafka).enqueue(eq("alert.generated:501"), eq("alerts.generated"), eq("12"), anyString());
    }

    @Test
    void allowWithoutFlagsRaisesNothing() {
        DecisionRecordWorkflowService.Outcome outcome = service.raise(record("ALLOW", 0, List.of(), false, false, null));
        assertNull(outcome.alertId());
        verify(alerts, never()).save(any());
        verify(webhooks, never()).enqueueForPsp(any(), any(), any(), any());
    }

    @Test
    void allowWithCtrFlagStillOpensTheCtrWorkflow() {
        DecisionRecordWorkflowService.Outcome outcome = service.raise(record("ALLOW", 0, List.of(), false, true, null));
        assertEquals(501L, outcome.alertId());
        assertTrue(outcome.caseIds().isEmpty(), "an ALLOW with a CTR flag is a filing task, not a case");
        assertEquals(900L, outcome.ctrDeadlineId());
    }

    @Test
    void failClosedHoldRaisesAnAlertButNoCase() {
        Assessment record = record("HOLD", 0, List.of(), false, false, null);
        record.setFailClosedCode("NO_BUNDLE");
        DecisionRecordWorkflowService.Outcome outcome = service.raise(record);
        assertEquals(501L, outcome.alertId());
        assertTrue(outcome.caseIds().isEmpty());
        ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
        verify(alerts).save(alert.capture());
        assertTrue(alert.getValue().getReason().contains("NO_BUNDLE"));
        assertEquals("HIGH", alert.getValue().getSeverity());
    }

    @Test
    void ruleCasesFollowTheAutoCreateSwitch() {
        when(config.isRuleAutoCreateCasesEnabled()).thenReturn(false);
        DecisionRecordWorkflowService.Outcome outcome = service.raise(record("ALERT", 45, List.of(9001L), false, false, "421"));
        assertTrue(outcome.caseIds().isEmpty());
        verify(cases, never()).triggerCaseFromEdgeDecision(any(), anyLong(), anyString(), any(), any(), any(), any(),
                anyString(), anyString(), any(), anyLong());
    }

    @Test
    void workflowIsRaisedOncePerDecisionRecord() {
        when(alerts.existsByPspIdAndSourceTypeAndSourceReference(12L, "EDGE_DECISION", EDGE_ID.toString())).thenReturn(true);
        DecisionRecordWorkflowService.Outcome outcome = service.raise(record("BLOCK", 100, List.of(9001L), true, true, "421"));
        assertNull(outcome.alertId());
        verify(alerts, never()).save(any());
    }

    @Test
    void merchantOfAnotherPspIsNotLinked() {
        Assessment record = record("ALERT", 45, List.of(9001L), false, false, "999");
        service.raise(record);
        ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
        verify(alerts, org.mockito.Mockito.atLeastOnce()).save(alert.capture());
        assertNull(alert.getValue().getMerchantId());
        assertFalse(alert.getValue().getReason().isBlank());
    }

    /** A stored decision record row as DecisionRecordStore leaves it. */
    private static Assessment record(String decision, int score, List<Long> rules, boolean sanctions, boolean ctr,
                                     String merchantId) {
        Assessment a = new Assessment();
        a.setId(UUID.randomUUID());
        a.setPspId(12L);
        a.setEdgeAssessmentId(EDGE_ID);
        a.setTxnRef("AUTH-1");
        a.setRecordVersion(2);
        a.setDecision(decision);
        a.setScore(score);
        a.setTriggeredRuleIds(rules);
        a.setRuleVersionIds(rules.isEmpty() ? List.of() : List.of(91002L));
        a.setSanctionsHit(sanctions);
        a.setWatchlistEntryIds(sanctions ? List.of("ofac-sdn:36216") : List.of());
        a.setCtrRequired(ctr);
        a.setTypologyCodes(List.of());
        a.setMerchantId(merchantId);
        a.setEvaluatedAt(Instant.parse("2026-10-16T10:02:51Z"));
        return a;
    }
}
