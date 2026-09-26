package com.posgateway.aml.service;

import com.posgateway.aml.config.KafkaConfig;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.TransactionFeatures;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.TransactionFeaturesRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decision Engine
 * Applies configurable thresholds and rules from database
 * Determines actions: BLOCK, HOLD, ALERT, ALLOW
 */
@Service
public class DecisionEngine {

    private static final Logger logger = LoggerFactory.getLogger(DecisionEngine.class);

    private final ConfigService configService;
    private final AlertRepository alertRepository;
    private final TransactionFeaturesRepository featuresRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final com.posgateway.aml.service.kafka.KafkaOutboxService kafkaOutboxService;
    private final com.posgateway.aml.service.limits.TransactionLimitEnforcementService limitEnforcementService;
    private final com.posgateway.aml.service.security.PaymentBlacklistService paymentBlacklistService;

    @Autowired(required = false)
    private com.posgateway.aml.service.case_management.CaseCreationService caseCreationService;

    /**
     * How long (days) a card is auto-declined after a Do-Not-Honour (BLOCK) decision. The goal is
     * "decline that card for a month", so the default is 30. Tunable via {@code fraud.dnh.block-days}.
     */
    @org.springframework.beans.factory.annotation.Value("${fraud.dnh.block-days:30}")
    private int dnhBlockDays;

    @Autowired
    public DecisionEngine(ConfigService configService,
                         AlertRepository alertRepository,
                         TransactionFeaturesRepository featuresRepository,
                         TransactionRepository transactionRepository,
                         ObjectMapper objectMapper,
                         com.posgateway.aml.service.kafka.KafkaOutboxService kafkaOutboxService,
                         com.posgateway.aml.service.limits.TransactionLimitEnforcementService limitEnforcementService,
                         com.posgateway.aml.service.security.PaymentBlacklistService paymentBlacklistService) {
        this.configService = configService;
        this.alertRepository = alertRepository;
        this.featuresRepository = featuresRepository;
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
        this.kafkaOutboxService = kafkaOutboxService;
        this.limitEnforcementService = limitEnforcementService;
        this.paymentBlacklistService = paymentBlacklistService;
    }

    /**
     * Evaluate transaction and determine action
     * 
     * @param transaction Transaction entity
     * @param score Fraud score from ML model
     * @param features Feature map
     * @return Decision result
     */
    @Transactional
    public DecisionResult evaluate(TransactionEntity transaction, Double score, Map<String, Object> features) {
        return evaluate(transaction, score, features, null, null);
    }

    /**
     * Evaluate with the per-transaction scoring latency. Latency is persisted on
     * transaction_features so MonitoringMetricsService can compute true p50/p95/p99
     * percentiles from observed data instead of placeholder constants.
     */
    @Transactional
    public DecisionResult evaluate(TransactionEntity transaction, Double score,
                                   Map<String, Object> features, Long latencyMs) {
        return evaluate(transaction, score, features, latencyMs, null);
    }

    @Transactional
    public DecisionResult evaluate(TransactionEntity transaction, Double score,
                                   Map<String, Object> features, Long latencyMs,
                                   Map<String, Object> riskDetails) {
        logger.info("Evaluating transaction {} with score {}", transaction.getTxnId(), score);
        applyRuleEvidence(transaction, riskDetails);

        // Merchant-configured limits (hard block)
        DecisionResult limitResult = checkTransactionLimits(transaction);
        if (limitResult != null) {
            applyDecisionAction(transaction, limitResult);
            saveFeaturesAndDecision(transaction, score, features, riskDetails, limitResult, latencyMs);
            return limitResult;
        }

        // Check sanctions / blacklist hard rules before ML scoring path
        DecisionResult hardRuleResult = checkHardRules(transaction);
        if (hardRuleResult != null) {
            logger.info("Hard rule triggered for transaction {}: {}",
                transaction.getTxnId(), hardRuleResult.getAction());
            applyDecisionAction(transaction, hardRuleResult);
            saveFeaturesAndDecision(transaction, score, features, riskDetails, hardRuleResult, latencyMs);
            return hardRuleResult;
        }

        // Apply DB rule engine outcomes (SpEL + Drools from rule_definitions)
        DecisionResult ruleDecision = applyRuleEngineDecision(transaction, score, riskDetails);
        if (ruleDecision != null) {
            checkAmlRules(transaction, features, ruleDecision, ruleDecision.getReasons());
            saveFeaturesAndDecision(transaction, score, features, riskDetails, ruleDecision, latencyMs);
            logger.info("Decision for transaction {} from rules: {} (score={})",
                transaction.getTxnId(), ruleDecision.getAction(), score);
            return ruleDecision;
        }

        // Apply model-based thresholds (from database)
        Double blockThreshold = configService.getFraudBlockThreshold();
        Double holdThreshold = configService.getFraudHoldThreshold();

        final boolean isBlock = score >= blockThreshold;
        final boolean isHold = score >= holdThreshold;

        if (isBlock) {
            List<String> reasons = new ArrayList<>();
            reasons.add(String.format("Score %.3f >= block threshold %.3f", score, blockThreshold));
            takeBlockAction(transaction, score, reasons);
            DecisionResult decision = new DecisionResult("BLOCK", score, reasons);
            checkAmlRules(transaction, features, decision, reasons);
            saveFeaturesAndDecision(transaction, score, features, riskDetails, decision, latencyMs);
            logger.info("Decision for transaction {}: {} (score={})",
                transaction.getTxnId(), decision.getAction(), score);
            return decision;
        }

        if (isHold) {
            List<String> reasons = new ArrayList<>();
            reasons.add(String.format("Score %.3f >= hold threshold %.3f", score, holdThreshold));
            takeHoldAction(transaction, score, reasons);
            DecisionResult decision = new DecisionResult("HOLD", score, reasons);
            checkAmlRules(transaction, features, decision, reasons);
            saveFeaturesAndDecision(transaction, score, features, riskDetails, decision, latencyMs);
            logger.info("Decision for transaction {}: {} (score={})",
                transaction.getTxnId(), decision.getAction(), score);
            return decision;
        }

        List<String> reasons = new ArrayList<>();
        reasons.add(String.format("Score %.3f < hold threshold %.3f", score, holdThreshold));
        DecisionResult decision = new DecisionResult("ALLOW", score, reasons);

        checkAmlRules(transaction, features, decision, reasons);
        saveFeaturesAndDecision(transaction, score, features, riskDetails, decision, latencyMs);
        maybeConsultAi(transaction, score, features, decision);
        logger.info("Decision for transaction {}: {} (score={})",
            transaction.getTxnId(), decision.getAction(), score);
        return decision;
    }

    private void maybeConsultAi(TransactionEntity transaction, Double score,
                                 Map<String, Object> features, DecisionResult decision) {
        if (aiEngineAdvisor == null || score == null) {
            return;
        }
        Double holdThreshold = configService.getFraudHoldThreshold();
        Double blockThreshold = configService.getFraudBlockThreshold();
        boolean borderline = aiEngineAdvisor.isBorderlineTransactionScore(score, holdThreshold, blockThreshold)
                || "ALERT".equals(decision.getAction())
                || "REVIEW".equals(decision.getAction());
        if (!borderline) {
            return;
        }
        Map<String, Object> aiFeatures = new HashMap<>();
        aiFeatures.put("score", score);
        aiFeatures.put("action", decision.getAction());
        aiFeatures.put("reasons", decision.getReasons());
        if (features != null) {
            aiFeatures.putAll(features);
        }
        if (transaction.getAmountCents() != null) {
            aiFeatures.put("amount_cents", transaction.getAmountCents());
        }
        if (transaction.getCurrency() != null) {
            aiFeatures.put("currency", transaction.getCurrency());
        }
        aiEngineAdvisor.adviseAsync(
                com.posgateway.aml.service.ai.decision.AiEngineType.TRANSACTION_RISK,
                transaction.getPspId(),
                decision.getAction(),
                aiFeatures,
                transaction.getTxnId(),
                null,
                null,
                null);
    }

    private DecisionResult applyRuleEngineDecision(TransactionEntity transaction, Double score,
                                                   Map<String, Object> riskDetails) {
        if (riskDetails == null || riskDetails.isEmpty()) {
            return null;
        }
        Object decisionObj = riskDetails.get("rule_decision");
        if (decisionObj == null) {
            return null;
        }
        String ruleDecision = decisionObj.toString().toUpperCase();
        @SuppressWarnings("unchecked")
        List<String> triggered = riskDetails.get("rules_triggered") instanceof List
                ? (List<String>) riskDetails.get("rules_triggered") : List.of();
        @SuppressWarnings("unchecked")
        List<String> ruleReasons = riskDetails.get("rule_reasons") instanceof List
                ? (List<String>) riskDetails.get("rule_reasons") : List.of();

        if (triggered.isEmpty() && "ALLOW".equals(ruleDecision)) {
            return null;
        }

        List<String> reasons = new ArrayList<>(ruleReasons);
        if (reasons.isEmpty() && !triggered.isEmpty()) {
            reasons.add("Rules triggered: " + String.join(", ", triggered));
        }

        String action = mapRuleDecisionToAction(ruleDecision);
        if ("ALLOW".equals(action) && triggered.isEmpty()) {
            return null;
        }

        if ("BLOCK".equals(action)) {
            takeBlockAction(transaction, score, reasons);
        } else if ("HOLD".equals(action)) {
            takeHoldAction(transaction, score, reasons);
        } else if ("ALERT".equals(action) || "REVIEW".equals(action)) {
            createAlert(transaction, score, "ALERT", String.join("; ", reasons));
        }

        maybeAutoCreateCases(transaction, action, triggered, reasons);

        return new DecisionResult(action, score, reasons);
    }

    private void maybeAutoCreateCases(TransactionEntity transaction, String action,
                                      List<String> triggered, List<String> reasons) {
        if (caseCreationService == null || !configService.isRuleAutoCreateCasesEnabled()) {
            return;
        }
        if (!"BLOCK".equals(action) && !"HOLD".equals(action) && !"ALERT".equals(action) && !"REVIEW".equals(action)) {
            return;
        }
        String description = reasons.isEmpty()
                ? "Rule engine decision: " + action
                : String.join("; ", reasons);
        if (triggered.isEmpty()) {
            caseCreationService.triggerCaseFromRule(transaction, "RULE_ENGINE", description);
            return;
        }
        for (String ruleName : triggered) {
            caseCreationService.triggerCaseFromRule(transaction, ruleName, description);
        }
    }

    private String mapRuleDecisionToAction(String ruleDecision) {
        return switch (ruleDecision) {
            case "BLOCK", "SUSPEND" -> "BLOCK";
            case "HOLD" -> "HOLD";
            case "ALERT", "FLAG", "REVIEW" -> "ALERT";
            default -> "ALLOW";
        };
    }

    private DecisionResult checkTransactionLimits(TransactionEntity transaction) {
        Optional<com.posgateway.aml.service.limits.TransactionLimitEnforcementService.LimitBreach> limitBreach =
                limitEnforcementService.checkLimits(transaction);
        if (limitBreach.isEmpty()) {
            return null;
        }
        List<String> reasons = new ArrayList<>(limitBreach.get().reasons());
        return new DecisionResult("BLOCK", 1.0, reasons);
    }

    private DecisionResult checkHardRules(TransactionEntity transaction) {
        List<String> reasons = new ArrayList<>();
        if (configService.isBlacklistEnabled()) {
            String panHash = transaction.getPanHash();
            if (panHash != null && paymentBlacklistService.isBlacklisted("pan", panHash)) {
                reasons.add("BLACKLIST: PAN hash is blacklisted");
            }
            String terminalId = transaction.getTerminalId();
            if (terminalId != null && paymentBlacklistService.isBlacklisted("terminal", terminalId)) {
                reasons.add("BLACKLIST: Terminal is blacklisted");
            }
            String ip = transaction.getIpAddress();
            if (ip != null && paymentBlacklistService.isBlacklisted("ip", ip)) {
                reasons.add("BLACKLIST: IP address is blacklisted");
            }
        }
        if (!reasons.isEmpty()) {
            return new DecisionResult("BLOCK", 1.0, reasons);
        }

        DecisionResult sanctionsResult = checkSanctionsScreening(transaction);
        if (sanctionsResult != null) {
            return sanctionsResult;
        }

        if (crossPspFraudService != null) {
            DecisionResult crossPspResult = checkCrossPspFraud(transaction);
            if (crossPspResult != null) {
                return crossPspResult;
            }
        }

        return null;
    }

    @Autowired(required = false)
    private com.posgateway.aml.service.sanctions.RealTimeTransactionScreeningService realTimeScreeningService;

    @Autowired(required = false)
    private com.posgateway.aml.service.fraud.CrossPspFraudIntelligenceService crossPspFraudService;

    /**
     * W36-2 fix: the PSP-facing webhook feature (entity, repository, WebhookService.sendWebhook,
     * and the newly-added subscribe controller) existed end to end except for the one thing that
     * actually makes it useful — nothing anywhere in the codebase ever called sendWebhook, so a
     * PSP that subscribed would never receive a single delivery. This is the RISK_ALERT trigger:
     * every alert this engine creates now also fans out to that PSP's active RISK_ALERT webhook
     * subscriptions, reusing the exact event payload already built for the Kafka outbox below.
     */
    @Autowired(required = false)
    private com.posgateway.aml.service.psp.WebhookOutboxService webhookOutboxService;

    @Autowired(required = false)
    private com.posgateway.aml.service.ai.decision.AiEngineAdvisor aiEngineAdvisor;

    private DecisionResult checkSanctionsScreening(TransactionEntity transaction) {
        if (realTimeScreeningService == null) {
            return new DecisionResult("HOLD", 1.0,
                    List.of("SANCTIONS_SCREENING_UNAVAILABLE: manual compliance review required"));
        }
        
        try {
            com.posgateway.aml.service.sanctions.RealTimeTransactionScreeningService.TransactionScreeningResult result =
                    realTimeScreeningService.screenTransaction(transaction);

            if (result.isScreeningUnavailable()) {
                return new DecisionResult("HOLD", 1.0,
                        List.of("SANCTIONS_SCREENING_UNAVAILABLE: manual compliance review required"));
            }
            
            if (result.hasMatches() && result.shouldBlock()) {
                List<String> reasons = new ArrayList<>();
                reasons.add("SANCTIONS_MATCH: Transaction involves sanctioned entity");
                for (var match : result.getMatches()) {
                    reasons.add(String.format("Match: %s (%s) - %s", 
                        match.getScreenedName(), 
                        match.getEntityType(),
                        match.getScreeningResult().getStatus()));
                }
                
                logger.warn("Transaction {} blocked due to sanctions match: {}",
                    transaction.getTxnId(), reasons);
                
                return new DecisionResult("BLOCK", 1.0, reasons);
            }
        } catch (Exception e) {
            logger.error("Error during real-time sanctions screening for transaction {}: {}",
                transaction.getTxnId(), e.getMessage(), e);
            return new DecisionResult("HOLD", 1.0,
                    List.of("SANCTIONS_SCREENING_UNAVAILABLE: manual compliance review required"));
        }
        
        return null;
    }

    /**
     * Check transaction against cross-PSP fraud intelligence database.
     * If the merchant, PAN, or terminal has been flagged by another PSP,
     * the transaction is BLOCKED with appropriate reasons.
     */
    private DecisionResult checkCrossPspFraud(TransactionEntity transaction) {
        try {
            var result = crossPspFraudService.screenTransaction(transaction);
            if (result.hasMatch()) {
                List<String> reasons = new ArrayList<>();
                reasons.add("CROSS_PSP_FRAUD: Matched " + result.getMatchedEntities().size()
                        + " entity(ies) flagged by other PSPs");
                reasons.addAll(result.getMatchedEntities().stream()
                        .map(e -> "Cross-PSP match: " + e)
                        .toList());
                reasons.add("Total PSP flags: " + result.getTotalFlagCount());
                reasons.add("Highest risk: " + result.getHighestRiskLevel());

                // BLOCK if high risk or multiple PSPs flagged; otherwise FLAG
                String action = result.isHigh() || result.getTotalFlagCount() > 1 ? "BLOCK" : "HOLD";

                logger.warn("Transaction {} {} due to cross-PSP fraud match: {}",
                        transaction.getTxnId(), action, reasons);
                return new DecisionResult(action, 0.9, reasons);
            }
        } catch (Exception e) {
            logger.error("Cross-PSP fraud check failed for transaction {}: {}",
                    transaction.getTxnId(), e.getMessage());
            return new DecisionResult("HOLD", 0.8,
                    List.of("CROSS_PSP_SCREENING_UNAVAILABLE: manual fraud review required"));
        }
        return null;
    }

    private void applyDecisionAction(TransactionEntity transaction, DecisionResult result) {
        switch (result.getAction()) {
            case "BLOCK" -> takeBlockAction(transaction, result.getScore(), result.getReasons());
            case "HOLD" -> takeHoldAction(transaction, result.getScore(), result.getReasons());
            case "ALERT", "REVIEW" -> createAlert(
                    transaction, result.getScore(), "ALERT", String.join("; ", result.getReasons()));
            default -> {
                // ALLOW has no alert side effect.
            }
        }
    }

    private void checkAmlRules(TransactionEntity transaction, Map<String, Object> features,
                               DecisionResult decision, List<String> reasons) {
        Long amlThreshold = configService.getAmlHighValueThreshold();
        
        // Cache amountCents to avoid repeated method calls - early return if null
        Long amountCents = transaction.getAmountCents();
        if (amountCents == null) {
            return; // Cannot check AML rules without amount
        }
        
        // Cache threshold calculation
        final long thresholdValue = amlThreshold != null ? amlThreshold : Long.MAX_VALUE;
        // Capture the action BEFORE any AML escalation. BLOCK/HOLD paths have
        // already persisted an alert in take{Block,Hold}Action; only an ALLOW
        // entry action means no alert exists yet for this transaction.
        final String entryAction = decision.getAction();

        // Check single transaction amount
        if (amountCents >= thresholdValue) {
            reasons.add(String.format("AML: Amount %d >= threshold %d", amountCents, thresholdValue));

            // Escalate to compliance - use equals for string comparison
            String currentAction = decision.getAction();
            if ("ALLOW".equals(currentAction)) {
                decision.setAction("ALERT");
                reasons.add("AML escalation required");
            }
        }

        // Check cumulative amounts (from features)
        Object cumulativeDebits = features.get("cumulative_debits_30d");
        if (cumulativeDebits instanceof Number) {
            double cumulative = ((Number) cumulativeDebits).doubleValue();
            long cumulativeThreshold = thresholdValue * 10L; // 10x single transaction threshold
            if (cumulative >= cumulativeThreshold) {
                reasons.add(String.format("AML: Cumulative 30d amount %.2f >= threshold %d",
                    cumulative, cumulativeThreshold));
                if ("ALLOW".equals(decision.getAction())) {
                    decision.setAction("ALERT");
                }
            }
        }

        // An AML escalation on an otherwise-ALLOW transaction must still raise an
        // alert for compliance review. Previously the action flipped to "ALERT"
        // but no Alert row was ever written (createAlert was only reachable from
        // the BLOCK/HOLD paths), so high-value transactions silently produced no
        // alert. Fill that gap here without double-creating for BLOCK/HOLD.
        if ("ALERT".equals(decision.getAction()) && "ALLOW".equals(entryAction)) {
            createAlert(transaction, decision.getScore(), "ALERT", String.join("; ", reasons));
        }
    }

    private void takeBlockAction(TransactionEntity transaction, Double score, List<String> reasons) {
        // Block transaction - decline with response code
        transaction.setAcquirerResponse("05"); // Generic decline
        
        // Create alert
        createAlert(transaction, score, "BLOCK", String.join("; ", reasons));

        // "Do Not Honour": a BLOCK sets acquirer response 05, so we register this card for a
        // fixed decline window (30 days by default). Subsequent attempts on the same card then
        // short-circuit at the blacklist check in evaluate() (type "pan") until the window lapses.
        // Idempotent and best-effort — never breaks the decline path; a repeat DNH extends the window.
        String panHash = transaction.getPanHash();
        if (panHash != null && !panHash.isBlank()) {
            paymentBlacklistService.addToBlacklist("pan", panHash,
                    "Do Not Honour — auto-declined by DecisionEngine: " + String.join("; ", reasons),
                    null, LocalDateTime.now().plusDays(dnhBlockDays));
        }
    }

    private void takeHoldAction(TransactionEntity transaction, Double score, List<String> reasons) {
        // Hold for review - soft decline or accept with flag
        transaction.setAcquirerResponse("01"); // Refer to card issuer
        
        // Create alert for manual review
        createAlert(transaction, score, "HOLD", String.join("; ", reasons));
    }

    private void createAlert(TransactionEntity transaction, Double score, String action, String reason) {
        Alert alert = new Alert();
        alert.setTxnId(transaction.getTxnId());
        alert.setScore(score);
        alert.setAction(action);
        alert.setReason(reason);
        alert.setStatus("open");
        alert.setMerchantId(parseMerchantId(transaction.getMerchantId()));
        alert.setPspId(transaction.getPspId());
        alert.setSourceType("TRANSACTION_MONITORING");
        alert.setSourceReference(String.valueOf(transaction.getTxnId()));
        alert.setSeverity(resolveSeverity(action, score));
        alert.setSarRequired(transaction.isSarRequired());
        alert.setCtrRequired(transaction.isCtrRequired());
        alert.setTriggeredRules(transaction.getTriggeredRules());

        Alert saved = alertRepository.save(alert);
        logger.info("Created alert for transaction {}: {} - {}",
            transaction.getTxnId(), action, reason);

        if (aiEngineAdvisor != null) {
            Map<String, Object> alertFeatures = new HashMap<>();
            alertFeatures.put("action", action);
            alertFeatures.put("reason", reason);
            alertFeatures.put("score", score);
            alertFeatures.put("severity", saved.getSeverity());
            aiEngineAdvisor.adviseAsync(
                    com.posgateway.aml.service.ai.decision.AiEngineType.ALERT_TRIAGE,
                    transaction.getPspId(),
                    action,
                    alertFeatures,
                    transaction.getTxnId(),
                    saved.getAlertId(),
                    null,
                    null);
        }

        // The alert and its event are committed atomically through the outbox.
        publishAlertGeneratedEvent(saved, transaction);
    }

    private void publishAlertGeneratedEvent(Alert alert, TransactionEntity transaction) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("alertId",    alert.getAlertId());
            event.put("txnId",      alert.getTxnId());
            event.put("action",     alert.getAction());
            event.put("score",      alert.getScore());
            event.put("status",     alert.getStatus());
            event.put("severity",   alert.getSeverity());
            event.put("sarRequired", alert.isSarRequired());
            event.put("ctrRequired", alert.isCtrRequired());
            event.put("triggeredRules", alert.getTriggeredRules());
            event.put("pspId",      transaction.getPspId());
            event.put("merchantId", transaction.getMerchantId());
            event.put("timestamp",  java.time.LocalDateTime.now().toString());

            String payload = objectMapper.writeValueAsString(event);
            String partitionKey = transaction.getPspId() != null
                    ? String.valueOf(transaction.getPspId()) : "0";
            kafkaOutboxService.enqueue(
                    "alert.generated:" + alert.getAlertId(),
                    KafkaConfig.TOPIC_ALERTS_GENERATED,
                    partitionKey,
                    payload);
            logger.debug("Queued alert-generated event: alertId={}", alert.getAlertId());

            // Durable webhook delivery: enqueued in the same transaction as the alert row.
            if (webhookOutboxService != null && transaction.getPspId() != null) {
                webhookOutboxService.enqueueForPsp(
                        transaction.getPspId(),
                        "RISK_ALERT",
                        event,
                        "webhook.risk_alert:" + alert.getAlertId());
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to serialize or enqueue alert event for alertId=" + alert.getAlertId(), e);
        }
    }

    private Long parseMerchantId(String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(merchantId);
        } catch (NumberFormatException invalid) {
            logger.warn("Alert merchantId is not a numeric platform ID: {}", merchantId);
            return null;
        }
    }

    private String resolveSeverity(String action, Double score) {
        if ("BLOCK".equalsIgnoreCase(action) || (score != null && score >= 0.9)) {
            return "CRITICAL";
        }
        if ("HOLD".equalsIgnoreCase(action) || (score != null && score >= 0.7)) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private void applyRuleEvidence(TransactionEntity transaction, Map<String, Object> riskDetails) {
        if (riskDetails == null || riskDetails.isEmpty()) {
            return;
        }
        transaction.setSarRequired(Boolean.TRUE.equals(riskDetails.get("sar_required")));
        transaction.setCtrRequired(Boolean.TRUE.equals(riskDetails.get("ctr_required")));
        if (riskDetails.get("regulatory_evidence") instanceof Map<?, ?> regulatoryEvidence) {
            transaction.setCtrEvaluationStatus(stringValue(regulatoryEvidence.get("ctrEvaluationStatus")));
            transaction.setCtrUsdEquivalent(decimalValue(regulatoryEvidence.get("amountUsd")));
            transaction.setCtrThresholdUsd(decimalValue(regulatoryEvidence.get("ctrThresholdUsd")));
            transaction.setCtrRateSource(stringValue(regulatoryEvidence.get("rateSource")));
            transaction.setCtrRateEffectiveAt(localDateTimeValue(regulatoryEvidence.get("rateEffectiveAt")));
            transaction.setCtrEvaluatedAt(localDateTimeValue(regulatoryEvidence.get("evaluatedAt")));
        }
        Object ruleDecision = riskDetails.get("rule_decision");
        transaction.setRuleDecision(ruleDecision != null ? ruleDecision.toString() : null);
        Object triggeredRules = riskDetails.get("rules_triggered");
        try {
            transaction.setTriggeredRules(triggeredRules != null
                    ? objectMapper.writeValueAsString(triggeredRules)
                    : "[]");
        } catch (Exception serializationFailure) {
            throw new IllegalStateException("Failed to serialize triggered rule evidence", serializationFailure);
        }
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private java.math.BigDecimal decimalValue(Object value) {
        if (value instanceof java.math.BigDecimal decimal) return decimal;
        if (value instanceof Number number) return java.math.BigDecimal.valueOf(number.doubleValue());
        if (value == null) return null;
        try {
            return new java.math.BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDateTime localDateTimeValue(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value.toString());
        } catch (java.time.format.DateTimeParseException ignored) {
            return null;
        }
    }

    private void saveFeaturesAndDecision(TransactionEntity transaction, Double score,
                                        Map<String, Object> features, Map<String, Object> riskDetails,
                                        DecisionResult decision,
                                        Long latencyMs) {
        try {
            transaction.setDecision(decision.getAction());
            transaction.setRiskLevel(resolveFinalRiskLevel(decision.getAction(), transaction.getRiskLevel()));
            transactionRepository.save(transaction);

            TransactionFeatures txnFeatures = new TransactionFeatures();
            txnFeatures.setTxnId(transaction.getTxnId());
            txnFeatures.setScore(score);
            txnFeatures.setActionTaken(decision.getAction());
            if (latencyMs != null) {
                txnFeatures.setLatencyMs(latencyMs.intValue());
            }
            // Denormalize psp_id so per-PSP metrics queries don't need a join
            txnFeatures.setPspId(transaction.getPspId());

            String featureJson = objectMapper.writeValueAsString(features);
            txnFeatures.setFeatureJson(featureJson);
            txnFeatures.setRiskDetails(objectMapper.writeValueAsString(
                    riskDetails != null ? riskDetails : Map.of()));
            if (riskDetails != null && riskDetails.get("model_version") != null) {
                txnFeatures.setModelVersion(riskDetails.get("model_version").toString());
            }

            featuresRepository.save(txnFeatures);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to persist final decision evidence for transaction " + transaction.getTxnId(), e);
        }
    }

    private String resolveFinalRiskLevel(String action, String currentRiskLevel) {
        return switch (action) {
            case "BLOCK" -> "CRITICAL";
            case "HOLD" -> "HIGH";
            case "ALERT", "REVIEW" -> "MEDIUM";
            default -> currentRiskLevel != null ? currentRiskLevel : "LOW";
        };
    }

    /**
     * Decision Result
     */
    public static class DecisionResult {
        private String action; // BLOCK, HOLD, ALERT, ALLOW
        private Double score;
        private List<String> reasons;

        public DecisionResult(String action, Double score, List<String> reasons) {
            this.action = action;
            this.score = score;
            this.reasons = reasons;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public List<String> getReasons() {
            return reasons;
        }

        public void setReasons(List<String> reasons) {
            this.reasons = reasons;
        }
    }
}

