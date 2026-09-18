package com.posgateway.aml.service.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.alert.AlertTuningRecommendation;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.alert.AlertTuningRecommendationRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.rules.RuleEffectivenessService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates evidence-based rule tuning proposals and applies approved parameter changes. */
@Service
public class AlertTuningService {

    private static final Logger logger = LoggerFactory.getLogger(AlertTuningService.class);
    private static final TypeReference<LinkedHashMap<String, Object>> PARAMETER_MAP = new TypeReference<>() {};

    private final AlertTuningRecommendationRepository recommendationRepository;
    private final RuleDefinitionRepository ruleRepository;
    private final RuleEffectivenessService effectivenessService;
    private final UserRepository userRepository;
    private final PspIsolationService pspIsolationService;
    private final ObjectMapper objectMapper;

    private final com.posgateway.aml.service.rules.RuleGovernanceService governanceService;

    public AlertTuningService(AlertTuningRecommendationRepository recommendationRepository,
            RuleDefinitionRepository ruleRepository,
            RuleEffectivenessService effectivenessService,
            UserRepository userRepository,
            PspIsolationService pspIsolationService,
            ObjectMapper objectMapper,
            com.posgateway.aml.service.rules.RuleGovernanceService governanceService) {
        this.recommendationRepository = recommendationRepository;
        this.ruleRepository = ruleRepository;
        this.effectivenessService = effectivenessService;
        this.userRepository = userRepository;
        this.pspIsolationService = pspIsolationService;
        this.objectMapper = objectMapper;
        this.governanceService = governanceService;
    }

    @Transactional
    public AlertTuningRecommendation suggestTuning(String ruleName) {
        RuleDefinition rule = ruleRepository.findFirstByNameOrderByIdAsc(ruleName)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleName));
        validateRuleAccess(rule);

        List<AlertTuningRecommendation> pending = recommendationRepository
                .findByRuleIdAndStatus(rule.getId(), "PENDING");
        if (!pending.isEmpty()) {
            return pending.get(0);
        }

        RuleEffectivenessService.RuleEffectivenessDTO metrics = effectivenessService.compute(rule.getId());
        long reviewedExecutions = metrics.getTruePositives() + metrics.getFalsePositives();
        if (metrics.getTotalExecutions() == 0 || reviewedExecutions == 0) {
            throw new IllegalStateException(
                    "Rule tuning requires real execution and investigator disposition data");
        }

        double falsePositiveRate = metrics.getFalsePositiveRate();
        Map<String, Object> currentParameters = parseParameters(rule.getParameters());
        Map<String, Object> proposedParameters = proposeParameters(currentParameters, falsePositiveRate);
        boolean actionable = !proposedParameters.equals(currentParameters);

        AlertTuningRecommendation recommendation = new AlertTuningRecommendation();
        recommendation.setRuleId(rule.getId());
        recommendation.setRuleName(rule.getName());
        recommendation.setFalsePositiveRate(falsePositiveRate);
        recommendation.setOriginalParameters(writeParameters(currentParameters));
        recommendation.setProposedParameters(actionable ? writeParameters(proposedParameters) : null);
        recommendation.setRecommendation(buildRecommendation(
                currentParameters, proposedParameters, falsePositiveRate, reviewedExecutions));
        recommendation.setPriority(determinePriority(falsePositiveRate));
        recommendation.setStatus(actionable ? "PENDING" : "NO_ACTION");
        recommendation.setMetricsSnapshot(metricsSnapshot(metrics));
        recommendation.setCreatedAt(LocalDateTime.now());

        logger.info("Generated {} tuning analysis for rule {} from {} reviewed executions",
                recommendation.getStatus(), rule.getName(), reviewedExecutions);
        return recommendationRepository.save(recommendation);
    }

    public List<AlertTuningRecommendation> getPendingRecommendations() {
        return recommendationRepository.findByStatus("PENDING");
    }

    @Transactional
    public void applyRecommendation(Long recommendationId, String appliedByUsername) {
        AlertTuningRecommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));
        if (!"PENDING".equals(recommendation.getStatus())) {
            throw new IllegalStateException("Only pending recommendations can be applied");
        }
        if (recommendation.getRuleId() == null || recommendation.getProposedParameters() == null) {
            throw new IllegalStateException("Recommendation does not contain an actionable rule parameter change");
        }

        RuleDefinition rule = ruleRepository.findById(recommendation.getRuleId())
                .orElseThrow(() -> new IllegalStateException("Rule no longer exists: " + recommendation.getRuleId()));
        User appliedBy = userRepository.findByUsername(appliedByUsername)
                .orElseThrow(() -> new IllegalArgumentException("Applying user not found: " + appliedByUsername));
        validateRuleMutation(rule, appliedBy);

        String currentParameters = writeParameters(parseParameters(rule.getParameters()));
        if (!Objects.equals(currentParameters, recommendation.getOriginalParameters())) {
            throw new IllegalStateException("Rule parameters changed after this recommendation was generated");
        }

        Map<String, Object> proposedParameters = parseParameters(recommendation.getProposedParameters());

        // Route the change through maker/checker instead of writing the rule directly. A direct
        // ruleRepository.save() here produced no RuleVersion, required no second approver and did not
        // reload the engine — leaving holes in the rule_versions audit trail that every other
        // mutation path is forced to fill. The patch carries ONLY the tuned parameters; merge() is
        // null-guarded, so no other field is touched.
        RuleDefinition patch = new RuleDefinition();
        patch.setParameters(writeParameters(proposedParameters));
        governanceService.proposeUpdate(rule, patch, appliedBy,
                "Apply alert-tuning recommendation " + recommendationId);

        recommendation.setStatus("APPLIED");
        recommendation.setAppliedBy(appliedBy.getId());
        recommendation.setAppliedAt(LocalDateTime.now());
        recommendationRepository.save(recommendation);
        logger.info("Applied tuning recommendation {} to rule {}", recommendationId, rule.getName());
    }

    private Map<String, Object> proposeParameters(Map<String, Object> current, double falsePositiveRate) {
        if (falsePositiveRate <= 0.30 || current.isEmpty()) return new LinkedHashMap<>(current);

        String parameter = current.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Number)
                .filter(entry -> isSensitivityParameter(entry.getKey()))
                .min(Comparator.comparingInt(entry -> parameterPriority(entry.getKey())))
                .map(Map.Entry::getKey)
                .orElse(null);
        if (parameter == null) return new LinkedHashMap<>(current);

        Map<String, Object> proposed = new LinkedHashMap<>(current);
        Number existing = (Number) current.get(parameter);
        double increase = falsePositiveRate > 0.50 ? 1.25 : 1.10;
        double adjusted = existing.doubleValue() * increase;
        Object typedValue = existing instanceof Byte || existing instanceof Short
                || existing instanceof Integer || existing instanceof Long
                ? (long) Math.ceil(adjusted)
                : Math.round(adjusted * 1_000_000.0) / 1_000_000.0;
        proposed.put(parameter, typedValue);
        return proposed;
    }

    private boolean isSensitivityParameter(String key) {
        String normalized = key.toLowerCase();
        if (normalized.contains("lookback") || normalized.contains("window_days")) return false;
        return normalized.contains("threshold")
                || normalized.startsWith("min_")
                || normalized.startsWith("max_")
                || normalized.contains("multiplier")
                || normalized.contains("ratio")
                || normalized.contains("count")
                || normalized.contains("share")
                || normalized.contains("uses")
                || normalized.contains("changes");
    }

    private int parameterPriority(String key) {
        String normalized = key.toLowerCase();
        if (normalized.contains("threshold")) return 0;
        if (normalized.contains("multiplier") || normalized.contains("ratio")) return 1;
        if (normalized.startsWith("min_") || normalized.startsWith("max_")) return 2;
        return 3;
    }

    private String buildRecommendation(Map<String, Object> current, Map<String, Object> proposed,
            double falsePositiveRate, long reviewedExecutions) {
        if (current.equals(proposed)) {
            return String.format(
                    "No automatic parameter change proposed. False-positive rate is %.2f%% across %d reviewed matches, or the rule has no numeric sensitivity parameter.",
                    falsePositiveRate * 100.0, reviewedExecutions);
        }
        String changedKey = proposed.keySet().stream()
                .filter(key -> !Objects.equals(current.get(key), proposed.get(key)))
                .findFirst()
                .orElseThrow();
        return String.format(
                "Increase %s from %s to %s; observed false-positive rate is %.2f%% across %d reviewed matches.",
                changedKey, current.get(changedKey), proposed.get(changedKey),
                falsePositiveRate * 100.0, reviewedExecutions);
    }

    private String determinePriority(double falsePositiveRate) {
        if (falsePositiveRate > 0.50) return "HIGH";
        if (falsePositiveRate > 0.30) return "MEDIUM";
        return "LOW";
    }

    private Map<String, Object> metricsSnapshot(RuleEffectivenessService.RuleEffectivenessDTO metrics) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("totalExecutions", metrics.getTotalExecutions());
        snapshot.put("truePositives", metrics.getTruePositives());
        snapshot.put("falsePositives", metrics.getFalsePositives());
        snapshot.put("falsePositiveRate", metrics.getFalsePositiveRate());
        snapshot.put("averageExecutionTimeMs", metrics.getAverageExecutionTimeMs());
        if (metrics.getLastExecuted() != null) snapshot.put("lastExecuted", metrics.getLastExecuted().toString());
        return snapshot;
    }

    private Map<String, Object> parseParameters(String parameters) {
        if (parameters == null || parameters.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(parameters, PARAMETER_MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Rule parameters are not valid JSON", e);
        }
    }

    private String writeParameters(Map<String, Object> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize rule parameters", e);
        }
    }

    private void validateRuleAccess(RuleDefinition rule) {
        if (rule.getPspId() != null) pspIsolationService.validatePspAccess(rule.getPspId());
    }

    private void validateRuleMutation(RuleDefinition rule, User user) {
        if (pspIsolationService.isPlatformAdministrator(user)) return;
        if (rule.isSystemManaged() || rule.getPspId() == null || user.getPsp() == null
                || !Objects.equals(rule.getPspId(), user.getPsp().getPspId())) {
            throw new SecurityException("PSP users may only tune rules owned by their PSP");
        }
    }
}
