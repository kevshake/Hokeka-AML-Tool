package com.posgateway.aml.service.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.rules.*;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.repository.rules.RuleVersionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RuleGovernanceService {

    private final RuleDefinitionRepository ruleRepository;
    private final RuleVersionRepository versionRepository;
    private final DroolsRulesService droolsRulesService;
    private final ObjectMapper objectMapper;
    private final SpelRuleExecutor spelRuleExecutor;

    public RuleGovernanceService(RuleDefinitionRepository ruleRepository,
            RuleVersionRepository versionRepository,
            DroolsRulesService droolsRulesService,
            ObjectMapper objectMapper,
            SpelRuleExecutor spelRuleExecutor) {
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.droolsRulesService = droolsRulesService;
        this.objectMapper = objectMapper;
        this.spelRuleExecutor = spelRuleExecutor;
    }

    /**
     * Reject an unsafe or syntactically-invalid SpEL rule expression at authoring time — before it is
     * proposed or activated — so an operator/LLM cannot store an RCE attempt and a typo cannot force
     * every transaction to HOLD at runtime. No-op for non-SPEL rule types or blank expressions.
     */
    private void validateSpelExpression(String ruleType, String ruleExpression) {
        if ("SPEL".equalsIgnoreCase(ruleType) && ruleExpression != null && !ruleExpression.isBlank()) {
            spelRuleExecutor.validateExpression(ruleExpression);
        }
    }

    /**
     * Strip every field a client must never set on a create. The rules API binds the JPA entity
     * straight from the request body, so without this a caller could supply:
     * <ul>
     *   <li>{@code id} — turning the subsequent {@code save()} into an UPDATE of an arbitrary
     *       existing rule (including another tenant's, or a locked system rule);</li>
     *   <li>{@code systemManaged} — making its own rule undeletable and identity-locked;</li>
     *   <li>{@code externalCode} / {@code derivedFromRuleId} — forging catalogue provenance;</li>
     *   <li>{@code currentVersionId} / {@code pendingVersionId} / {@code currentVersionNumber} —
     *       corrupting the maker/checker audit chain.</li>
     * </ul>
     * Ownership and lifecycle are assigned by the caller of this method, never by the client.
     */
    private void sanitizeClientSuppliedFields(RuleDefinition proposed) {
        proposed.setId(null);
        proposed.setSystemManaged(false);
        proposed.setExternalCode(null);
        proposed.setDerivedFromRuleId(null);
        proposed.setCurrentVersionId(null);
        proposed.setPendingVersionId(null);
    }

    @Transactional
    public RuleDefinition proposeCreate(RuleDefinition proposed, User maker, Long pspId, String summary) {
        sanitizeClientSuppliedFields(proposed);
        // Names are unique per owner, so a PSP may have a rule whose name matches another PSP's or
        // a system default's — only a clash within the same owner (pspId) is rejected.
        ruleRepository.findByNameAndPspId(proposed.getName(), pspId).ifPresent(existing -> {
            throw new IllegalArgumentException("A rule named '" + proposed.getName() + "' already exists");
        });
        // Reject unsafe/invalid SpEL up front so the maker gets an immediate, clear error.
        validateSpelExpression(proposed.getRuleType(), proposed.getRuleExpression());
        Map<String, Object> snapshot = snapshot(proposed);
        boolean requestedEnabled = bool(snapshot.get("enabled"));
        proposed.setEnabled(false);
        proposed.setPspId(pspId);
        proposed.setCreatedBy(maker.getId());
        proposed.setLifecycleStatus(RuleLifecycleStatus.PENDING_APPROVAL);
        proposed.setCurrentVersionNumber(0);
        RuleDefinition saved = ruleRepository.save(proposed);
        snapshot.put("enabled", requestedEnabled);
        RuleVersion version = createVersion(saved, snapshot, RuleChangeType.CREATE, maker,
                summaryOrDefault(summary, "Create rule"), null);
        saved.setPendingVersionId(version.getId());
        return ruleRepository.save(saved);
    }

    @Transactional
    public RuleDefinition proposeUpdate(RuleDefinition rule, RuleDefinition patch, User maker,
            String summary) {
        ensureNoPendingVersion(rule);
        Map<String, Object> proposed = snapshot(rule);
        merge(proposed, patch, rule.isSystemManaged());
        RuleVersion version = createVersion(rule, proposed, RuleChangeType.UPDATE, maker,
                summaryOrDefault(summary, "Update rule"), null);
        markPending(rule, version);
        return ruleRepository.save(rule);
    }

    @Transactional
    public RuleDefinition proposeEnabled(RuleDefinition rule, boolean enabled, User maker) {
        ensureNoPendingVersion(rule);
        Map<String, Object> proposed = snapshot(rule);
        proposed.put("enabled", enabled);
        RuleVersion version = createVersion(rule, proposed,
                enabled ? RuleChangeType.ENABLE : RuleChangeType.DISABLE,
                maker, enabled ? "Enable rule" : "Disable rule", null);
        markPending(rule, version);
        return ruleRepository.save(rule);
    }

    @Transactional
    public RuleDefinition proposeRetirement(RuleDefinition rule, User maker, String summary) {
        if (rule.isSystemManaged()) {
            throw new IllegalArgumentException("System-managed rules cannot be retired; submit a disable proposal");
        }
        if (rule.getDerivedFromRuleId() != null) {
            throw new IllegalArgumentException(
                    "A PSP's copy of a default rule cannot be retired; disable it instead");
        }
        ensureNoPendingVersion(rule);
        Map<String, Object> proposed = snapshot(rule);
        proposed.put("enabled", false);
        RuleVersion version = createVersion(rule, proposed, RuleChangeType.RETIRE, maker,
                summaryOrDefault(summary, "Retire rule"), null);
        markPending(rule, version);
        return ruleRepository.save(rule);
    }

    @Transactional
    public RuleDefinition proposeRollback(RuleDefinition rule, Long targetVersionId, User maker,
            String summary) {
        ensureNoPendingVersion(rule);
        RuleVersion target = versionRepository.findById(targetVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Rule version not found"));
        if (!target.getRule().getId().equals(rule.getId())) {
            throw new IllegalArgumentException("Target version belongs to a different rule");
        }
        RuleVersion version = createVersion(rule, new LinkedHashMap<>(target.getSnapshot()),
                RuleChangeType.ROLLBACK, maker,
                summaryOrDefault(summary, "Rollback to version " + target.getVersionNumber()), target);
        markPending(rule, version);
        return ruleRepository.save(rule);
    }

    @Transactional
    public RuleVersionView approve(Long versionId, User reviewer, LocalDateTime effectiveFrom,
            String reason) {
        RuleVersion version = pendingVersion(versionId);
        if (version.getCreatedBy().getId().equals(reviewer.getId())) {
            throw new SecurityException("Rule maker cannot approve their own change");
        }
        assertSamePsp(version, reviewer);
        LocalDateTime now = LocalDateTime.now();
        version.setReviewedBy(reviewer);
        version.setReviewedAt(now);
        version.setReviewReason(requireReason(reason));
        version.setEffectiveFrom(effectiveFrom != null ? effectiveFrom : now);
        version.setLifecycleStatus(RuleLifecycleStatus.APPROVED);
        versionRepository.save(version);
        if (!version.getEffectiveFrom().isAfter(now)) {
            activate(version, now);
        } else {
            RuleDefinition rule = version.getRule();
            rule.setLifecycleStatus(RuleLifecycleStatus.APPROVED);
            ruleRepository.save(rule);
        }
        return toView(version);
    }

    @Transactional
    public RuleVersionView reject(Long versionId, User reviewer, String reason) {
        RuleVersion version = pendingVersion(versionId);
        if (version.getCreatedBy().getId().equals(reviewer.getId())) {
            throw new SecurityException("Rule maker cannot review their own change");
        }
        assertSamePsp(version, reviewer);
        LocalDateTime now = LocalDateTime.now();
        version.setLifecycleStatus(RuleLifecycleStatus.REJECTED);
        version.setReviewedBy(reviewer);
        version.setReviewedAt(now);
        version.setReviewReason(requireReason(reason));
        versionRepository.save(version);
        RuleDefinition rule = version.getRule();
        rule.setPendingVersionId(null);
        rule.setLifecycleStatus(rule.getCurrentVersionId() == null
                ? RuleLifecycleStatus.REJECTED : RuleLifecycleStatus.ACTIVE);
        ruleRepository.save(rule);
        return toView(version);
    }

    @Scheduled(cron = "${rules.governance.activation-cron:0 * * * * *}")
    @Transactional
    public void activateApprovedVersions() {
        LocalDateTime now = LocalDateTime.now();
        versionRepository.findByLifecycleStatusAndEffectiveFromLessThanEqual(
                RuleLifecycleStatus.APPROVED, now).forEach(version -> activate(version, now));
    }

    @Transactional(readOnly = true)
    public List<RuleVersionView> pendingFor(User user) {
        return versionRepository.findByLifecycleStatusOrderBySubmittedAtAsc(RuleLifecycleStatus.PENDING_APPROVAL)
                .stream().filter(version -> visibleTo(version, user)).map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<RuleVersionView> history(Long ruleId, User user) {
        List<RuleVersion> versions = versionRepository.findByRuleIdOrderByVersionNumberDesc(ruleId);
        if (!versions.isEmpty() && !visibleTo(versions.get(0), user)) {
            throw new SecurityException("Rule belongs to a different PSP");
        }
        return versions.stream().map(this::toView).toList();
    }

    private RuleVersion createVersion(RuleDefinition rule, Map<String, Object> proposed,
            RuleChangeType changeType, User maker, String summary, RuleVersion supersedes) {
        int nextVersion = versionRepository.findFirstByRuleIdOrderByVersionNumberDesc(rule.getId())
                .map(existing -> existing.getVersionNumber() + 1).orElse(1);
        RuleVersion version = new RuleVersion();
        version.setRule(rule);
        version.setVersionNumber(nextVersion);
        version.setLifecycleStatus(RuleLifecycleStatus.PENDING_APPROVAL);
        version.setChangeType(changeType);
        version.setSnapshot(new LinkedHashMap<>(proposed));
        version.setContentHash(hash(proposed));
        version.setChangeSummary(summary);
        version.setPspId(rule.getPspId());
        version.setCreatedBy(maker);
        version.setSupersedesVersion(supersedes);
        return versionRepository.save(version);
    }

    private void activate(RuleVersion version, LocalDateTime now) {
        RuleDefinition rule = version.getRule();
        if (rule.getCurrentVersionId() != null) {
            versionRepository.findById(rule.getCurrentVersionId()).ifPresent(current -> {
                current.setLifecycleStatus(RuleLifecycleStatus.SUPERSEDED);
                versionRepository.save(current);
            });
        }
        apply(rule, version.getSnapshot());
        version.setLifecycleStatus(version.getChangeType() == RuleChangeType.RETIRE
                ? RuleLifecycleStatus.RETIRED : RuleLifecycleStatus.ACTIVE);
        version.setActivatedAt(now);
        versionRepository.save(version);
        rule.setCurrentVersionId(version.getId());
        rule.setCurrentVersionNumber(version.getVersionNumber());
        rule.setPendingVersionId(null);
        rule.setLifecycleStatus(version.getChangeType() == RuleChangeType.RETIRE
                ? RuleLifecycleStatus.RETIRED : RuleLifecycleStatus.ACTIVE);
        ruleRepository.save(rule);
        droolsRulesService.reloadRules();
    }

    private Map<String, Object> snapshot(RuleDefinition rule) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", rule.getName());
        value.put("description", rule.getDescription());
        value.put("ruleJson", rule.getRuleJson());
        value.put("drlContent", rule.getDrlContent());
        value.put("ruleType", rule.getRuleType());
        value.put("ruleExpression", rule.getRuleExpression());
        value.put("score", rule.getScore());
        value.put("action", rule.getAction());
        value.put("priority", rule.getPriority());
        value.put("enabled", rule.isEnabled());
        value.put("systemManaged", rule.isSystemManaged());
        value.put("category", rule.getCategory());
        value.put("ruleSubtype", rule.getRuleSubtype());
        value.put("appliesTo", rule.getAppliesTo());
        value.put("typology", rule.getTypology());
        value.put("checksFor", rule.getChecksFor());
        value.put("externalCode", rule.getExternalCode());
        value.put("recommended", rule.isRecommended());
        value.put("sampleUseCase", rule.getSampleUseCase());
        value.put("parameters", rule.getParameters());
        return value;
    }

    private void merge(Map<String, Object> target, RuleDefinition patch, boolean systemManaged) {
        if (!systemManaged && patch.getName() != null) target.put("name", patch.getName());
        put(target, "description", patch.getDescription());
        put(target, "ruleJson", patch.getRuleJson());
        put(target, "drlContent", patch.getDrlContent());
        put(target, "ruleType", patch.getRuleType());
        put(target, "ruleExpression", patch.getRuleExpression());
        put(target, "score", patch.getScore());
        put(target, "action", patch.getAction());
        put(target, "priority", patch.getPriority());
        // `enabled` is deliberately NOT taken from the patch. RuleDefinition.enabled is a primitive
        // that defaults to TRUE, so a PUT body omitting the field deserialises to true and would
        // silently re-arm a rule that was deliberately disabled (e.g. editing only a description).
        // Enablement changes go through the dedicated POST /rules/{id}/enable|disable endpoints;
        // `target` already carries the rule's current state from snapshot().
        if (!systemManaged) {
            put(target, "category", patch.getCategory());
            put(target, "ruleSubtype", patch.getRuleSubtype());
            put(target, "appliesTo", patch.getAppliesTo());
        }
        put(target, "typology", patch.getTypology());
        put(target, "checksFor", patch.getChecksFor());
        put(target, "parameters", patch.getParameters());
    }

    private void apply(RuleDefinition rule, Map<String, Object> value) {
        String proposedName = string(value.get("name"));
        ruleRepository.findByNameAndPspId(proposedName, rule.getPspId())
                .filter(existing -> !existing.getId().equals(rule.getId()))
                .ifPresent(existing -> { throw new IllegalArgumentException("Rule name is already in use"); });
        rule.setName(proposedName);
        rule.setDescription(string(value.get("description")));
        rule.setRuleJson(string(value.get("ruleJson")));
        rule.setDrlContent(string(value.get("drlContent")));
        rule.setRuleType(string(value.get("ruleType")));
        rule.setRuleExpression(string(value.get("ruleExpression")));
        // Activation guarantee: an unsafe/invalid SpEL expression must never become live.
        validateSpelExpression(rule.getRuleType(), rule.getRuleExpression());
        rule.setScore(integer(value.get("score")));
        rule.setAction(string(value.get("action")));
        rule.setPriority(integer(value.get("priority")));
        rule.setEnabled(bool(value.get("enabled")));
        rule.setSystemManaged(bool(value.get("systemManaged")));
        rule.setCategory(string(value.get("category")));
        rule.setRuleSubtype(string(value.get("ruleSubtype")));
        rule.setAppliesTo(string(value.get("appliesTo")));
        rule.setTypology(string(value.get("typology")));
        rule.setChecksFor(string(value.get("checksFor")));
        rule.setExternalCode(string(value.get("externalCode")));
        rule.setRecommended(bool(value.get("recommended")));
        rule.setSampleUseCase(string(value.get("sampleUseCase")));
        rule.setParameters(string(value.get("parameters")));
    }

    private RuleVersion pendingVersion(Long id) {
        RuleVersion version = versionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule version not found"));
        if (version.getLifecycleStatus() != RuleLifecycleStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Rule version is not pending approval");
        }
        return version;
    }

    private void markPending(RuleDefinition rule, RuleVersion version) {
        rule.setPendingVersionId(version.getId());
        rule.setLifecycleStatus(RuleLifecycleStatus.PENDING_APPROVAL);
    }

    private void ensureNoPendingVersion(RuleDefinition rule) {
        if (rule.getPendingVersionId() != null) {
            throw new IllegalStateException("Rule already has a pending change proposal");
        }
    }

    private boolean visibleTo(RuleVersion version, User user) {
        if (user.getPsp() == null) return true;
        return version.getPspId() == null || version.getPspId().equals(user.getPsp().getPspId());
    }

    private void assertSamePsp(RuleVersion version, User user) {
        if (!visibleTo(version, user)) throw new SecurityException("Rule belongs to a different PSP");
        if (version.getPspId() == null && user.getPsp() != null) {
            throw new SecurityException("Only platform reviewers may approve global rules");
        }
    }

    private String hash(Map<String, Object> snapshot) {
        try {
            byte[] canonical = objectMapper.writeValueAsString(new TreeMap<>(snapshot))
                    .getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to hash rule version", e);
        }
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }

    private String string(Object value) { return value != null ? String.valueOf(value) : null; }
    private Integer integer(Object value) {
        if (value == null) return null;
        return value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString());
    }
    private boolean bool(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue
                : value != null && Boolean.parseBoolean(value.toString());
    }
    private String summaryOrDefault(String summary, String fallback) {
        return summary == null || summary.isBlank() ? fallback : summary.trim();
    }
    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Review reason is required");
        return reason.trim();
    }

    private RuleVersionView toView(RuleVersion version) {
        return new RuleVersionView(version.getId(), version.getRule().getId(), version.getRule().getName(),
                version.getVersionNumber(), version.getLifecycleStatus(), version.getChangeType(),
                version.getSnapshot(), version.getContentHash(), version.getChangeSummary(), version.getPspId(),
                version.getCreatedBy().getId(), version.getCreatedBy().getUsername(), version.getCreatedAt(),
                version.getEffectiveFrom(), version.getReviewedBy() != null ? version.getReviewedBy().getId() : null,
                version.getReviewedBy() != null ? version.getReviewedBy().getUsername() : null,
                version.getReviewedAt(), version.getReviewReason(), version.getActivatedAt());
    }

    public record RuleVersionView(Long id, Long ruleId, String ruleName, int versionNumber,
            RuleLifecycleStatus lifecycleStatus, RuleChangeType changeType, Map<String, Object> snapshot,
            String contentHash, String changeSummary, Long pspId, Long createdById, String createdByUsername,
            LocalDateTime createdAt, LocalDateTime effectiveFrom, Long reviewedById,
            String reviewedByUsername, LocalDateTime reviewedAt, String reviewReason,
            LocalDateTime activatedAt) {}
}
