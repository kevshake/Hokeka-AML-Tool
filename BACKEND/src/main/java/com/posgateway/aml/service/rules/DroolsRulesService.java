package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.rules.RuleEvaluationResult;
import com.posgateway.aml.rules.RuleFeatureKeys;
import com.posgateway.aml.rules.TransactionFact;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drools Rules Engine Service for AML Regulatory Compliance.
 * 
 * Provides deterministic rule evaluation for:
 * - Configured regulatory compliance decisions
 * - ML score thresholds
 * - Velocity-based rules
 * - Dynamic Rules from Database (RuleDefinition)
 * 
 * Results cached in Redis for short-lived decision retrieval.
 */
@Service
public class DroolsRulesService {

    private static final Logger logger = LoggerFactory.getLogger(DroolsRulesService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RuleDefinitionRepository ruleRepository;

    private KieContainer kieContainer;
    private boolean droolsEnabled = false;

    /** Number of DB DRL rules skipped at the last reload because they did not compile. Surfaced so a
     *  single malformed tenant rule is visible instead of silently disabling the whole engine. */
    private volatile int skippedDrlRuleCount = 0;

    /**
     * Maps each DB-sourced DRL rule name to the PSP that owns it (null = a global/system rule).
     * All enabled DRL rules across every tenant compile into one shared KieContainer; this map lets
     * the {@link AgendaFilter} at fire time restrict a tenant's rule to that tenant's transactions,
     * so PSP A's custom DRL can never fire on (or block) PSP B's traffic. Rebuilt on each reload.
     */
    private final Map<String, Long> dynamicRulePspId = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public DroolsRulesService(
            RedisTemplate<String, Object> redisTemplate,
            RuleDefinitionRepository ruleRepository) {
        this.redisTemplate = redisTemplate;
        this.ruleRepository = ruleRepository;
    }

    @PostConstruct
    public void initializeRules() {
        reloadRules();
    }

    public synchronized void reloadRules() {
        logger.info("Initializing/Reloading Drools Rules Engine...");
        try {
            KieServices kieServices = KieServices.Factory.get();
            KieFileSystem kfs = kieServices.newKieFileSystem();
            boolean rulesFound = false;

            // 1. Try to load DRL from classpath (Static Fallback)
            try {
                kfs.write("src/main/resources/rules/aml-rules.drl",
                        kieServices.getResources().newClassPathResource("rules/aml-rules.drl"));
                rulesFound = true;
                logger.debug("Loaded static rules from classpath.");
            } catch (Exception e) {
                logger.debug("No static DRL file found on classpath (this is expected if fully dynamic).");
            }
            
            // 2. Load Dynamic Rules from Database
            dynamicRulePspId.clear();
            skippedDrlRuleCount = 0;
            List<RuleDefinition> dynamicRules = ruleRepository.findByEnabledTrueOrderByPriorityDesc();
            if (!dynamicRules.isEmpty()) {
                logger.info("loading {} dynamic rules from database.", dynamicRules.size());
                for (RuleDefinition rule : dynamicRules) {
                    if (rule.getDrlContent() != null && !rule.getDrlContent().isBlank()) {
                        // Pre-validate each rule standalone so ONE malformed tenant rule cannot fail the
                        // whole build and silently disable the entire engine — skip only the bad one.
                        if (!drlCompiles(kieServices, rule.getDrlContent())) {
                            skippedDrlRuleCount++;
                            logger.error("Skipping DRL rule '{}' (psp {}) — it does not compile; the rest "
                                    + "of the engine stays up", rule.getName(), rule.getPspId());
                            continue;
                        }
                        String path = "src/main/resources/rules/dynamic/" + rule.getName() + ".drl";
                        kfs.write(path, kieServices.getResources().newByteArrayResource(rule.getDrlContent().getBytes()));
                        // Record ownership so a PSP's DRL rule only fires on that PSP's transactions.
                        // Global/system rules (null pspId) are deliberately left out of the map —
                        // absence means "fires for everyone" (and ConcurrentHashMap forbids null values).
                        if (rule.getPspId() != null) {
                            dynamicRulePspId.put(rule.getName(), rule.getPspId());
                        }
                        rulesFound = true;
                    }
                }
            }
            if (skippedDrlRuleCount > 0) {
                logger.warn("Drools reload skipped {} malformed DRL rule(s); the engine remains active "
                        + "for the valid rules.", skippedDrlRuleCount);
            }

            if (rulesFound) {
                KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();

                if (kieBuilder.getResults().hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
                    logger.error("Drools rule errors: {}", kieBuilder.getResults().getMessages());
                    droolsEnabled = false;
                } else {
                    KieModule kieModule = kieBuilder.getKieModule();
                    this.kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
                    droolsEnabled = true;
                    logger.info("Drools Rules Engine initialized successfully.");
                }
            } else {
                logger.warn("No compiled DRL rules found; decisioning relies on DB dynamic rules "
                        + "(RulesExecutionService) and DecisionEngine thresholds.");
                droolsEnabled = false;
            }

        } catch (Exception e) {
            logger.error("Unexpected error during Drools engine initialization: {}", e.getMessage(), e);
            droolsEnabled = false;
        }
        if (!droolsEnabled) {
            logger.info("Drools has no compiled DRL; evaluation is DB-dynamic-rules only (no hardcoded rules).");
        }
    }

    /**
     * Evaluate transaction against all AML rules.
     * Uses Drools for rule evaluation and caches results in Redis.
     */
    public RuleEvaluationResult evaluate(Long txnId, Map<String, Object> features, Double mlScore) {
        long startTime = System.currentTimeMillis();

        // Build transaction fact from features
        TransactionFact fact = buildTransactionFact(txnId, features, mlScore);

        // Evaluate rules, scoped to this transaction's PSP so a tenant's DRL never fires cross-tenant.
        int rulesExecuted = evaluateRules(fact, resolvePspId(features));

        long evaluationTime = System.currentTimeMillis() - startTime;

        RuleEvaluationResult result = new RuleEvaluationResult(
                txnId,
                fact.getDecision(),
                new ArrayList<>(fact.getReasons()),
                new ArrayList<>(fact.getTriggeredRules()),
                fact.isSarRequired(),
                fact.isCtrRequired(),
                rulesExecuted,
                evaluationTime);

        // Cache result in Redis for audit trail (24h TTL, mirrors prior decision-cache behavior)
        try {
            Map<String, Object> entry = new HashMap<>();
            entry.put("decision", fact.getDecision());
            entry.put("finalScore", mlScore);
            entry.put("reasons", fact.getReasons() != null ? String.join("|", fact.getReasons()) : "");
            entry.put("triggeredRules", String.join(",", fact.getTriggeredRules()));
            entry.put("decidedAt", System.currentTimeMillis());
            redisTemplate.opsForValue().set("graph:risk:" + txnId, entry, Duration.ofHours(24));
        } catch (Exception e) {
            logger.warn("Error caching risk decision for txn {}: {}", txnId, e.getMessage());
        }

        logger.info("Rules evaluated for txn {}: decision={}, rules={}, time={}ms",
                txnId, result.getDecision(), result.getTriggeredRules().size(), evaluationTime);

        return result;
    }

    public TransactionFact buildTransactionFactPublic(Long txnId, Map<String, Object> features, Double mlScore) {
        return buildTransactionFact(txnId, features, mlScore);
    }



    private TransactionFact buildTransactionFact(Long txnId, Map<String, Object> features, Double mlScore) {
        // Feature-map keys come from the registry (RuleFeatureKeys) so a consumer can never again read
        // a key no producer writes. PAN_AMOUNT_SUM_24H fixes the prior bug where this read the
        // non-existent "pan_txn_amount_sum_24h" and the fact's 24h PAN sum was permanently zero.
        TransactionFact fact = new TransactionFact(
                txnId,
                (String) features.getOrDefault(RuleFeatureKeys.MERCHANT_ID, "UNKNOWN"),
                toBigDecimal(features.get(RuleFeatureKeys.AMOUNT)),
                (String) features.getOrDefault(RuleFeatureKeys.CURRENCY, "USD"),
                (String) features.getOrDefault(RuleFeatureKeys.COUNTRY_CODE, "UNK"),
                LocalDateTime.now(),
                (String) features.getOrDefault(RuleFeatureKeys.CHANNEL, "POS"),
                (String) features.get(RuleFeatureKeys.PAN_HASH),
                mlScore,
                toDouble(features.get(RuleFeatureKeys.PAGE_RANK)),
                toLong(features.get(RuleFeatureKeys.COMMUNITY_ID)),
                toDouble(features.get(RuleFeatureKeys.BETWEENNESS)),
                toLong(features.get(RuleFeatureKeys.CONNECTION_COUNT)),
                toLong(features.get(RuleFeatureKeys.PAN_TXN_COUNT_1H)),
                toLong(features.get(RuleFeatureKeys.PAN_TXN_COUNT_24H)),
                toBoolean(features.get(RuleFeatureKeys.CASH_TRANSACTION)),
                toBoolean(features.get(RuleFeatureKeys.COUNTRY_HIGH_RISK)),
                toDouble(features.get(RuleFeatureKeys.PAN_AMOUNT_SUM_24H)),
                toDouble(features.get(RuleFeatureKeys.MERCHANT_AMOUNT_SUM_24H)),
                toDouble(features.get(RuleFeatureKeys.KRS_SCORE)),
                toDouble(features.get(RuleFeatureKeys.CRA_SCORE)),
                toDouble(features.get(RuleFeatureKeys.TRS_SCORE)));
        // MCC is enriched into the feature map from the merchant profile; expose it on the
        // fact so DRL/dynamic rules can target specific merchant category codes.
        Object mcc = features.get(RuleFeatureKeys.MCC);
        if (mcc != null) {
            fact.setMcc(String.valueOf(mcc));
        }
        return fact;
    }

    private int evaluateRules(TransactionFact fact, Long pspId) {
        if (droolsEnabled && kieContainer != null) {
            // Use Drools session
            KieSession session = kieContainer.newKieSession();
            try {
                session.insert(fact);
                // AgendaFilter: static/system rules (not in the map → null owner) always fire; a
                // DB DRL rule owned by a PSP fires only when it belongs to THIS transaction's PSP.
                return session.fireAllRules(tenantScopedFilter(pspId));
            } finally {
                session.dispose();
            }
        } else {
            // No compiled DRL available. Rule evaluation is fully DB-driven (dynamic rules edited by
            // PSPs/banks + each PSP's copies of the defaults), evaluated by RulesExecutionService;
            // there are deliberately NO hardcoded rules here. ML-score thresholds are still applied
            // independently by DecisionEngine, so decisioning is not lost.
            logger.debug("No compiled DRL rules loaded; relying on DB dynamic rules (no programmatic fallback).");
            return 0;
        }
    }

    /** True if a single DRL compiles cleanly on its own. Used to exclude a malformed rule from the
     *  shared build so it cannot disable the whole engine. */
    private boolean drlCompiles(KieServices kieServices, String drlContent) {
        try {
            KieFileSystem probe = kieServices.newKieFileSystem();
            probe.write("src/main/resources/rules/probe/probe.drl",
                    kieServices.getResources().newByteArrayResource(drlContent.getBytes()));
            KieBuilder kieBuilder = kieServices.newKieBuilder(probe).buildAll();
            return !kieBuilder.getResults().hasMessages(org.kie.api.builder.Message.Level.ERROR);
        } catch (Exception e) {
            return false;
        }
    }

    /** Number of DB DRL rules excluded at the last reload for not compiling (0 = all valid). */
    public int getSkippedDrlRuleCount() {
        return skippedDrlRuleCount;
    }

    /** Whether the Drools engine has a compiled rule set active. */
    public boolean isDroolsEnabled() {
        return droolsEnabled;
    }

    /**
     * Fires static/system rules (owner == null) for every transaction, but a PSP-owned DB DRL rule
     * only when it belongs to this transaction's PSP — preventing cross-tenant rule firing.
     */
    private AgendaFilter tenantScopedFilter(Long pspId) {
        return match -> {
            Long ruleOwner = dynamicRulePspId.get(match.getRule().getName());
            return ruleOwner == null || ruleOwner.equals(pspId);
        };
    }

    /** Best-effort numeric pspId from the feature map ({@code pspId} or {@code psp_id}). */
    private Long resolvePspId(Map<String, Object> features) {
        if (features == null) {
            return null;
        }
        Object value = features.get("pspId");
        if (value == null) {
            value = features.get("psp_id");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // Helper conversion methods
    private BigDecimal toBigDecimal(Object value) {
        if (value == null)
            return BigDecimal.ZERO;
        if (value instanceof BigDecimal)
            return (BigDecimal) value;
        if (value instanceof Number)
            return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Double toDouble(Object value) {
        if (value == null)
            return 0.0;
        if (value instanceof Number)
            return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Long toLong(Object value) {
        if (value == null)
            return 0L;
        if (value instanceof Number)
            return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        return value != null && Boolean.parseBoolean(value.toString());
    }
}
