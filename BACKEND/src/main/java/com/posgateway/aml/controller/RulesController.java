package com.posgateway.aml.controller;


import com.fasterxml.jackson.databind.ObjectMapper;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.ai.AiRuleGeneratorService;
import com.posgateway.aml.service.rules.DroolsRulesService;
import com.posgateway.aml.service.rules.DynamicRuleConverter;
import com.posgateway.aml.service.rules.RuleEffectivenessService;
import com.posgateway.aml.service.rules.RuleGovernanceService;
import com.posgateway.aml.service.security.PspIsolationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;




@RestController
@RequestMapping("/rules")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_USER')")
public class RulesController {

    private final RuleDefinitionRepository ruleRepository;
    private final AiRuleGeneratorService aiService;
    private final DroolsRulesService droolsService;
    private final DynamicRuleConverter converter;
    private final PspIsolationService pspIsolationService;
    private final UserRepository userRepository;
    private final RuleEffectivenessService effectivenessService;
    private final RuleGovernanceService governanceService;


    @Autowired
    public RulesController(
            RuleDefinitionRepository ruleRepository,
            AiRuleGeneratorService aiService,
            DroolsRulesService droolsService,
            DynamicRuleConverter converter,
            PspIsolationService pspIsolationService,
            UserRepository userRepository,
            RuleEffectivenessService effectivenessService,
            RuleGovernanceService governanceService) {
        this.ruleRepository = ruleRepository;
        this.aiService = aiService;
        this.droolsService = droolsService;
        this.converter = converter;
        this.pspIsolationService = pspIsolationService;
        this.userRepository = userRepository;
        this.effectivenessService = effectivenessService;
        this.governanceService = governanceService;
    }

    /**
     * Check if a rule is owned by super admin (pspId is null)
     */
    private boolean isSuperAdminRule(RuleDefinition rule) {
        return rule.getPspId() == null;
    }

    /**
     * Get current authenticated user
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String username = auth.getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateRule(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "prompt is required"));
        }
        RuleDefinition rule = aiService.generateRuleFromText(prompt);
        if (rule != null) {
            // Preview-only response — DO NOT persist. The FE shows the preview and the
            // operator clicks Save (POST /rules) to commit.
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("id", rule.getId());
            body.put("name", rule.getName());
            body.put("description", rule.getDescription());
            body.put("ruleType", rule.getRuleType());
            body.put("ruleExpression", rule.getRuleExpression());
            body.put("action", rule.getAction());
            body.put("score", rule.getScore());
            body.put("priority", rule.getPriority());
            body.put("enabled", rule.isEnabled());
            body.put("ruleJson", rule.getRuleJson());
            body.put("drlContent", rule.getDrlContent());
            body.put("pendingAdminApproval", true);
            Long auditId = aiService.getLastAuditId();
            if (auditId != null) {
                body.put("jevAuditId", auditId);
            }
            return ResponseEntity.ok(body);
        }
        if (!aiService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "AI rule generation not configured",
                            "hint", "set AI_RULE_GENERATOR_ENABLED=true and configure OPENROUTER_API_KEY + JEV_CHAT_MODEL"
                    ));
        }
        String detail = aiService.getLastErrorDetail();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "error", "AI rule generation failed",
                        "details", detail != null ? detail : "see server logs"
                ));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN') or hasAuthority('MANAGE_RULES')")
    public ResponseEntity<RuleDefinition> createRule(@RequestBody RuleDefinition rule) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // If JSON is provided but DRL is missing, auto-generate DRL
        if (rule.getDrlContent() == null || rule.getDrlContent().isBlank()) {
            if (rule.getRuleJson() != null && !rule.getRuleJson().isBlank()) {
                String drl = converter.convertJsonToDrl(rule.getName(), rule.getRuleJson());
                rule.setDrlContent(drl);
            }
        }
        
        // Set defaults
        if (rule.getPriority() == null) rule.setPriority(1);
        
        Long pspId;
        if (pspIsolationService.isPlatformAdministrator(currentUser)) {
            pspId = null;
        } else {
            pspId = pspIsolationService.getCurrentUserPspId();
        }
        RuleDefinition saved = governanceService.proposeCreate(rule, currentUser, pspId, "Create rule");
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<RuleDefinition>> getAllRules() {
        // HOK-40: filter by current user's PSP
        User currentUser = getCurrentUser();
        if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
            Long pspId = pspIsolationService.getCurrentUserPspId();
            if (pspId != null) {
                return ResponseEntity.ok(ruleRepository.findVisibleForPsp(pspId));
            }
        }
        return ResponseEntity.ok(ruleRepository.findAll());
    }

    /**
     * Get single rule by ID
     * GET /api/v1/rules/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<RuleDefinition> getRuleById(@PathVariable Long id) {
        RuleDefinition rule = ruleRepository.findById(id).orElse(null);
        if (rule == null) return ResponseEntity.notFound().build();
        if (!canAccess(rule, getCurrentUser())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(rule);
    }

    @PostMapping("/reload")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN') or hasAuthority('MANAGE_RULES')")
    public ResponseEntity<String> reloadRulesEngine() {
        droolsService.reloadRules();
        return ResponseEntity.ok("Rules engine reloaded successfully.");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN') or hasAuthority('MANAGE_RULES')")
    public ResponseEntity<RuleDefinition> updateRule(@PathVariable Long id, @RequestBody RuleDefinition rule) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RuleDefinition existing = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

        // Check if rule is owned by super admin
        if (isSuperAdminRule(existing)) {
            // Only super admin can modify super admin rules
            if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .build();
            }
        } else {
            // PSP users can only modify their own PSP's rules
            if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
                Long userPspId = pspIsolationService.getCurrentUserPspId();
                if (userPspId == null || !userPspId.equals(existing.getPspId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .build();
                }
            }
        }

        // System-managed rules: lock identity (name + external_code) so the
        // catalog stays referenceable from compliance docs. Everything else —
        // parameters, threshold, description, action, enabled — is editable.
        return ResponseEntity.ok(governanceService.proposeUpdate(existing, rule, currentUser, "Update rule"));
    }

    /**
     * Enable a rule
     * POST /api/v1/rules/{id}/enable
     */
    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN') or hasAuthority('MANAGE_RULES')")
    public ResponseEntity<RuleDefinition> enableRule(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RuleDefinition existing = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

        // Check authorization
        if (isSuperAdminRule(existing)) {
            if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else {
            if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
                Long userPspId = pspIsolationService.getCurrentUserPspId();
                if (userPspId == null || !userPspId.equals(existing.getPspId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }
        }

        return ResponseEntity.ok(governanceService.proposeEnabled(existing, true, currentUser));
    }

    /**
     * Disable a rule
     * POST /api/v1/rules/{id}/disable
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN') or hasAuthority('MANAGE_RULES')")
    public ResponseEntity<RuleDefinition> disableRule(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RuleDefinition existing = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

        // Check authorization
        if (isSuperAdminRule(existing)) {
            if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else {
            if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
                Long userPspId = pspIsolationService.getCurrentUserPspId();
                if (userPspId == null || !userPspId.equals(existing.getPspId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }
        }

        return ResponseEntity.ok(governanceService.proposeEnabled(existing, false, currentUser));
    }

    /**
     * Get rule effectiveness metrics
     * GET /api/v1/rules/{id}/effectiveness
     */
    @GetMapping("/{id}/effectiveness")
    public ResponseEntity<Map<String, Object>> getRuleEffectiveness(@PathVariable Long id) {
        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));
        if (!canAccess(rule, getCurrentUser())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        // Real aggregation against rule_execution_logs (V119). Disposition is back-filled
        // by AlertController#resolveAlert and AlertDispositionService#disposeAlert so the
        // truePositives/falsePositives counts reflect operator feedback.
        RuleEffectivenessService.RuleEffectivenessDTO metrics = effectivenessService.compute(rule.getId());

        Map<String, Object> effectiveness = new java.util.HashMap<>();
        effectiveness.put("ruleId", rule.getId());
        effectiveness.put("ruleName", rule.getName());
        effectiveness.put("totalExecutions", metrics.getTotalExecutions());
        effectiveness.put("truePositives", metrics.getTruePositives());
        effectiveness.put("falsePositives", metrics.getFalsePositives());
        effectiveness.put("falsePositiveRate", metrics.getFalsePositiveRate());
        effectiveness.put("averageExecutionTime", metrics.getAverageExecutionTimeMs());
        effectiveness.put("lastExecuted", metrics.getLastExecuted());

        return ResponseEntity.ok(effectiveness);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN') or hasAuthority('MANAGE_RULES')")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RuleDefinition existing = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

        // System-managed rules (the seeded default catalog) can never be deleted —
        // operators must use POST /rules/{id}/disable instead. They CAN edit
        // parameters / description / threshold via PUT.
        if (existing.isSystemManaged()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .header("X-Reason", "system-managed-rule")
                    .build();
        }

        // A PSP's copy of a default rule (derived_from_rule_id set) is part of the fixed initial
        // rule set — it can be edited or disabled but not deleted. Only PSP-created custom rules
        // are deletable.
        if (existing.getDerivedFromRuleId() != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .header("X-Reason", "default-rule-copy")
                    .build();
        }

        // Check if rule is owned by super admin
        if (isSuperAdminRule(existing)) {
            // Only super admin can delete super admin rules
            if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else {
            // PSP users can only delete their own PSP's rules
            if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
                Long userPspId = pspIsolationService.getCurrentUserPspId();
                if (userPspId == null || !userPspId.equals(existing.getPspId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }
        }

        governanceService.proposeRetirement(existing, currentUser, "Retire rule");
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/governance/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN') or hasAuthority('MANAGE_RULES')")
    public List<RuleGovernanceService.RuleVersionView> pendingChanges() {
        return governanceService.pendingFor(getCurrentUser());
    }

    @GetMapping("/{id}/versions")
    public List<RuleGovernanceService.RuleVersionView> versionHistory(@PathVariable Long id) {
        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));
        if (!canAccess(rule, getCurrentUser())) throw new SecurityException("Rule belongs to a different PSP");
        return governanceService.history(id, getCurrentUser());
    }

    @PostMapping("/governance/versions/{versionId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN') or hasAuthority('MANAGE_RULES')")
    public ResponseEntity<RuleGovernanceService.RuleVersionView> approveVersion(@PathVariable Long versionId,
            @RequestBody ReviewRuleVersionRequest request) {
        // W18-4 fix: getCurrentUser() returning null (authenticated principal whose username
        // doesn't resolve via userRepository -- an edge case, but a real one) used to flow
        // straight into governanceService.approve() unguarded, NPEing to a 500 instead of a
        // proper 401. createRule already null-checks the same way; matched here.
        User currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(
                governanceService.approve(versionId, currentUser, request.effectiveFrom(), request.reason()));
    }

    @PostMapping("/governance/versions/{versionId}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN') or hasAuthority('MANAGE_RULES')")
    public ResponseEntity<RuleGovernanceService.RuleVersionView> rejectVersion(@PathVariable Long versionId,
            @RequestBody ReviewRuleVersionRequest request) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(governanceService.reject(versionId, currentUser, request.reason()));
    }

    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN') or hasAuthority('MANAGE_RULES')")
    public ResponseEntity<RuleDefinition> proposeRollback(@PathVariable Long id, @RequestBody RollbackRuleRequest request) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));
        if (!canAccess(rule, currentUser)) throw new SecurityException("Rule belongs to a different PSP");
        return ResponseEntity.ok(
                governanceService.proposeRollback(rule, request.targetVersionId(), currentUser, request.reason()));
    }

    private boolean canAccess(RuleDefinition rule, User user) {
        if (user == null) return false;
        if (pspIsolationService.isPlatformAdministrator(user)) return true;
        Long pspId = pspIsolationService.getCurrentUserPspId();
        return rule.getPspId() == null || (pspId != null && pspId.equals(rule.getPspId()));
    }

    public record ReviewRuleVersionRequest(String reason, LocalDateTime effectiveFrom) {}
    public record RollbackRuleRequest(Long targetVersionId, String reason) {}
}

