package com.posgateway.aml.service.edge.records;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.KafkaConfig;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.alert.AlertFindingLink;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.Finding;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
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
import com.posgateway.aml.service.compliance.CtrFilingBridgeService;
import com.posgateway.aml.service.kafka.KafkaOutboxService;
import com.posgateway.aml.service.psp.WebhookOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Raises cloud workflow from an accepted decision record (contract 4.4, plan R7): the same
 * alert → case → CTR-filing chain the cloud pipeline ran for ingested transactions, but referencing
 * the transaction by {@code edgeAssessmentId} / {@code txnRef} only (I4) — no {@code transactions}
 * row is created or read, and nothing calls back into the Edge.
 *
 * <ul>
 *   <li><b>Alert</b> for every ALERT / HOLD / BLOCK decision, sanctions hit or CTR flag: source type
 *       {@value #SOURCE_TYPE}, source reference = edgeAssessmentId (one alert per record, enforced by
 *       the unique index {@code uk_alert_source_reference}). Reason text is rendered from rule ids.</li>
 *   <li><b>Case</b>: a sanctions hit always opens / joins a SANCTIONS_HIT case; an ALERT / HOLD / BLOCK
 *       rule decision does so as RULE_VIOLATION when {@code rules.auto-create-cases} is on (as in
 *       {@code DecisionEngine}). Fail-closed HOLDs raise an alert (the held payment needs a person)
 *       but no case: they are an operational state, not suspicion.</li>
 *   <li><b>CTR</b>: {@code ctrRequired} schedules a CTR_FILING compliance deadline sourced from the alert.</li>
 *   <li><b>Events</b>: {@code alerts.generated} (Kafka outbox) and the PSP's RISK_ALERT webhook carry
 *       ids and the decision only.</li>
 * </ul>
 */
@Service
public class DecisionRecordWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(DecisionRecordWorkflowService.class);

    /** alerts.source_type of alerts raised from decision records. */
    public static final String SOURCE_TYPE = "EDGE_DECISION";

    private final AlertRepository alerts;
    private final AlertFindingLinkRepository alertFindingLinks;
    private final FindingRepository findings;
    private final RuleDefinitionRepository rules;
    private final CaseCreationService caseCreation;
    private final ConfigService config;
    private final KafkaOutboxService kafkaOutbox;
    private final WebhookOutboxService webhookOutbox;
    private final ComplianceDeadlineRepository deadlines;
    private final PspRepository psps;
    private final MerchantRepository merchants;
    private final ObjectMapper objectMapper;

    public DecisionRecordWorkflowService(AlertRepository alerts,
                                         AlertFindingLinkRepository alertFindingLinks,
                                         FindingRepository findings,
                                         RuleDefinitionRepository rules,
                                         CaseCreationService caseCreation,
                                         ConfigService config,
                                         KafkaOutboxService kafkaOutbox,
                                         WebhookOutboxService webhookOutbox,
                                         ComplianceDeadlineRepository deadlines,
                                         PspRepository psps,
                                         MerchantRepository merchants,
                                         ObjectMapper objectMapper) {
        this.alerts = alerts;
        this.alertFindingLinks = alertFindingLinks;
        this.findings = findings;
        this.rules = rules;
        this.caseCreation = caseCreation;
        this.config = config;
        this.kafkaOutbox = kafkaOutbox;
        this.webhookOutbox = webhookOutbox;
        this.deadlines = deadlines;
        this.psps = psps;
        this.merchants = merchants;
        this.objectMapper = objectMapper;
    }

    /** What {@link #raise} produced, for the ingest log and tests. */
    public record Outcome(Long alertId, List<Long> caseIds, Long ctrDeadlineId) {
        static final Outcome NONE = new Outcome(null, List.of(), null);
    }

    /**
     * Raises the workflow for one stored decision record. Runs inside the record's ingest
     * transaction, so a failure rolls the record back and the Edge re-sends it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome raise(Assessment record) {
        if (!needsWorkflow(record)) {
            return Outcome.NONE;
        }
        String reference = record.getEdgeAssessmentId().toString();
        if (alerts.existsByPspIdAndSourceTypeAndSourceReference(record.getPspId(), SOURCE_TYPE, reference)) {
            log.debug("Workflow for decision record {} already raised", reference);
            return Outcome.NONE;
        }
        Map<Long, RuleDefinition> ruleIndex = ruleIndex(record);
        Alert alert = alerts.save(buildAlert(record, ruleIndex));
        linkFindings(alert, record);

        List<Long> caseIds = raiseCases(record, alert, ruleIndex);
        if (!caseIds.isEmpty()) {
            alert.setComplianceCaseId(caseIds.get(0));
            alerts.save(alert);
        }
        Long ctrDeadlineId = Boolean.TRUE.equals(record.getCtrRequired()) ? scheduleCtrFiling(record, alert) : null;
        publishAlertEvents(record, alert);
        return new Outcome(alert.getAlertId(), caseIds, ctrDeadlineId);
    }

    /** ALERT / HOLD / BLOCK, a sanctions hit or a CTR flag need a person to look. */
    static boolean needsWorkflow(Assessment record) {
        String decision = record.getDecision();
        return "ALERT".equals(decision) || "HOLD".equals(decision) || "BLOCK".equals(decision)
                || Boolean.TRUE.equals(record.getSanctionsHit()) || Boolean.TRUE.equals(record.getCtrRequired());
    }

    /** The cloud alert for a decision record; reason text rendered from rule ids, never from the Edge. */
    private Alert buildAlert(Assessment record, Map<Long, RuleDefinition> ruleIndex) {
        Alert alert = new Alert();
        alert.setTxnId(null);
        alert.setPspId(record.getPspId());
        alert.setMerchantId(ownMerchantId(record));
        alert.setScore(record.getScore() == null ? null : record.getScore().doubleValue());
        alert.setAction(record.getDecision());
        alert.setReason(renderReason(record, ruleIndex));
        alert.setStatus("open");
        alert.setSourceType(SOURCE_TYPE);
        alert.setSourceReference(record.getEdgeAssessmentId().toString());
        alert.setSeverity(severity(record));
        alert.setSarRequired(false);
        alert.setCtrRequired(Boolean.TRUE.equals(record.getCtrRequired()));
        alert.setTriggeredRules(triggeredRulesJson(record, ruleIndex));
        List<Long> versionIds = record.getRuleVersionIds() == null ? List.of() : record.getRuleVersionIds();
        if (versionIds.size() == 1) {
            alert.setRuleVersionId(versionIds.get(0));
        }
        alert.setSuspicionFormedAt(LocalDateTime.now());
        return alert;
    }

    /** Links the record's findings to the alert (investigation provenance). */
    private void linkFindings(Alert alert, Assessment record) {
        for (Finding finding : findings.findByAssessmentIdOrderByCreatedAtAsc(record.getId())) {
            AlertFindingLink link = new AlertFindingLink();
            link.setAlertId(alert.getAlertId());
            link.setFindingId(finding.getId());
            alertFindingLinks.save(link);
        }
    }

    /** Opens / joins the cases the record calls for and returns their ids. */
    private List<Long> raiseCases(Assessment record, Alert alert, Map<Long, RuleDefinition> ruleIndex) {
        List<Long> caseIds = new ArrayList<>();
        Long merchantId = alert.getMerchantId();
        String reference = record.getEdgeAssessmentId().toString();
        double score = record.getScore() == null ? 0.0 : record.getScore().doubleValue();
        if (Boolean.TRUE.equals(record.getSanctionsHit())) {
            String entries = record.getWatchlistEntryIds() == null ? "" : String.join(", ", record.getWatchlistEntryIds());
            ComplianceCase sanctions = caseCreation.triggerCaseFromEdgeDecision(merchantId, record.getPspId(),
                    "SANCTIONS_HIT", "Edge sanctions screening", null, null, 1.0,
                    "Sanctions/PEP match on Edge decision " + reference + " (watchlist entries: " + entries + ")",
                    reference, record.getTxnRef(), alert.getAlertId());
            addCaseId(caseIds, sanctions);
        }
        boolean ruleDecision = record.getFailClosedCode() == null
                && ("ALERT".equals(record.getDecision()) || "HOLD".equals(record.getDecision())
                || "BLOCK".equals(record.getDecision()));
        if (ruleDecision && Boolean.TRUE.equals(config.isRuleAutoCreateCasesEnabled())) {
            String description = renderReason(record, ruleIndex);
            List<Long> triggered = record.getTriggeredRuleIds() == null ? List.of() : record.getTriggeredRuleIds();
            if (triggered.isEmpty()) {
                addCaseId(caseIds, caseCreation.triggerCaseFromEdgeDecision(merchantId, record.getPspId(),
                        "RULE_VIOLATION", "EDGE_DECISION", null, null, score, description,
                        reference, record.getTxnRef(), alert.getAlertId()));
            }
            for (Long ruleId : triggered) {
                RuleDefinition rule = ruleIndex.get(ruleId);
                String ruleVersion = rule != null && rule.getCurrentVersionId() != null
                        ? String.valueOf(rule.getCurrentVersionId()) : "unversioned";
                addCaseId(caseIds, caseCreation.triggerCaseFromEdgeDecision(merchantId, record.getPspId(),
                        "RULE_VIOLATION", ruleLabel(ruleId, ruleIndex), String.valueOf(ruleId), ruleVersion,
                        score, description, reference, record.getTxnRef(), alert.getAlertId()));
            }
        }
        return caseIds;
    }

    /** Adds a case id once. */
    private static void addCaseId(List<Long> caseIds, ComplianceCase c) {
        if (c != null && c.getId() != null && !caseIds.contains(c.getId())) {
            caseIds.add(c.getId());
        }
    }

    /** CTR flagged on the Edge: schedule the filing deadline from the alert (the cloud has no transaction). */
    private Long scheduleCtrFiling(Assessment record, Alert alert) {
        if (deadlines.findBySourceTypeAndSourceId("ALERT", alert.getAlertId()).isPresent()) {
            return null;
        }
        ComplianceDeadline deadline = new ComplianceDeadline();
        deadline.setDeadlineType("CTR_FILING");
        deadline.setSourceType("ALERT");
        deadline.setSourceId(alert.getAlertId());
        deadline.setPspId(record.getPspId());
        deadline.setJurisdiction(psps.findById(record.getPspId()).map(p -> p.getCountry()).orElse(null));
        deadline.setDescription("CTR filing for Edge decision " + record.getEdgeAssessmentId()
                + (record.getTxnRef() != null ? " (transaction " + record.getTxnRef() + ")" : "")
                + ", alert " + alert.getAlertId());
        LocalDateTime evaluated = record.getEvaluatedAt() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(record.getEvaluatedAt(), java.time.ZoneId.systemDefault());
        deadline.setDeadlineDate(CtrFilingBridgeService.nextBusinessFriday(evaluated));
        return deadlines.save(deadline).getId();
    }

    /** alerts.generated + RISK_ALERT webhook, both with ids and the decision only. */
    private void publishAlertEvents(Assessment record, Alert alert) {
        Map<String, Object> ids = new LinkedHashMap<>();
        ids.put("alertId", alert.getAlertId());
        ids.put("edgeAssessmentId", record.getEdgeAssessmentId().toString());
        if (record.getTxnRef() != null) {
            ids.put("txnRef", record.getTxnRef());
        }
        ids.put("decision", record.getDecision());
        ids.put("pspId", record.getPspId());
        if (record.getMerchantId() != null) {
            ids.put("merchantId", record.getMerchantId());
        }
        if (alert.getComplianceCaseId() != null) {
            ids.put("caseId", alert.getComplianceCaseId());
        }
        ids.put("timestamp", LocalDateTime.now().toString());

        Map<String, Object> kafkaEvent = new LinkedHashMap<>(ids);
        // The reporting projection counts alerts by severity; severity is derived cloud-side.
        kafkaEvent.put("severity", alert.getSeverity());
        try {
            kafkaOutbox.enqueue("alert.generated:" + alert.getAlertId(), KafkaConfig.TOPIC_ALERTS_GENERATED,
                    String.valueOf(record.getPspId()), objectMapper.writeValueAsString(kafkaEvent));
        } catch (Exception e) {
            throw new IllegalStateException("cannot enqueue alert event for alert " + alert.getAlertId(), e);
        }
        webhookOutbox.enqueueForPsp(record.getPspId(), "RISK_ALERT", ids,
                "webhook.risk_alert:" + alert.getAlertId());
    }

    /** Rule definitions named by the record, by id. */
    private Map<Long, RuleDefinition> ruleIndex(Assessment record) {
        List<Long> ids = record.getTriggeredRuleIds() == null ? List.of() : record.getTriggeredRuleIds();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return rules.findAllById(ids).stream()
                .collect(Collectors.toMap(RuleDefinition::getId, Function.identity(), (a, b) -> a));
    }

    /** Reason text from rule ids, typology codes and the fail-closed code; nothing from the Edge's text. */
    static String renderReason(Assessment record, Map<Long, RuleDefinition> ruleIndex) {
        if (record.getFailClosedCode() != null) {
            return "Held by the Edge before any rule ran: " + describeFailClosed(record.getFailClosedCode())
                    + " (" + record.getFailClosedCode() + ")";
        }
        List<String> parts = new ArrayList<>();
        List<Long> triggered = record.getTriggeredRuleIds() == null ? List.of() : record.getTriggeredRuleIds();
        if (!triggered.isEmpty()) {
            parts.add("Rules triggered: " + triggered.stream()
                    .map(id -> ruleLabel(id, ruleIndex) + " (" + id + ")")
                    .collect(Collectors.joining(", ")));
        }
        if (record.getTypologyCodes() != null && !record.getTypologyCodes().isEmpty()) {
            parts.add("Typologies: " + String.join(", ", record.getTypologyCodes()));
        }
        if (Boolean.TRUE.equals(record.getSanctionsHit())) {
            parts.add("Sanctions/PEP match: " + String.join(", ", record.getWatchlistEntryIds() == null
                    ? List.of() : record.getWatchlistEntryIds()));
        }
        if (Boolean.TRUE.equals(record.getCtrRequired())) {
            parts.add("Cash transaction report required");
        }
        String head = "Edge decision " + record.getDecision() + " (score " + record.getScore() + ")";
        return parts.isEmpty() ? head : head + ". " + String.join("; ", parts);
    }

    /** Operator text for a fail-closed code. */
    private static String describeFailClosed(String code) {
        return switch (code) {
            case "NODE_NOT_AUTHORIZED" -> "the node was not authorised";
            case "NO_BUNDLE" -> "no verified rule bundle was in force";
            case "FEATURE_STORE_UNAVAILABLE" -> "the feature store was unavailable";
            case "LEDGER_UNAVAILABLE" -> "the Edge Ledger was unavailable";
            case "EVALUATOR_FAULT" -> "the rule evaluator failed";
            default -> "fail-closed gate";
        };
    }

    /** JSON array of triggered rule labels (as DecisionEngine stores triggered_rules). */
    private String triggeredRulesJson(Assessment record, Map<Long, RuleDefinition> ruleIndex) {
        List<String> labels = (record.getTriggeredRuleIds() == null ? List.<Long>of() : record.getTriggeredRuleIds())
                .stream().map(id -> ruleLabel(id, ruleIndex)).toList();
        try {
            return objectMapper.writeValueAsString(labels);
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialise triggered rules", e);
        }
    }

    /** A rule's name, or {@code rule:<id>} when the cloud no longer knows it. */
    private static String ruleLabel(Long id, Map<Long, RuleDefinition> ruleIndex) {
        RuleDefinition rule = ruleIndex.get(id);
        return rule != null && rule.getName() != null ? rule.getName() : "rule:" + id;
    }

    /** CRITICAL for a block or sanctions hit, HIGH for a hold, MEDIUM otherwise. */
    static String severity(Assessment record) {
        if ("BLOCK".equals(record.getDecision()) || Boolean.TRUE.equals(record.getSanctionsHit())) {
            return "CRITICAL";
        }
        if ("HOLD".equals(record.getDecision())) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    /**
     * The cloud merchant primary key named by the record, only when it is a merchant of the record's
     * own PSP (a foreign or unknown id stays on the decision record but never links workflow).
     */
    private Long ownMerchantId(Assessment record) {
        String merchantId = record.getMerchantId();
        if (merchantId == null || !merchantId.matches("[0-9]{1,18}")) {
            return null;
        }
        Long id = Long.valueOf(merchantId);
        return merchants.findByMerchantId(id)
                .filter(m -> m.getPsp() != null && record.getPspId().equals(m.getPsp().getPspId()))
                .map(m -> id)
                .orElse(null);
    }
}
