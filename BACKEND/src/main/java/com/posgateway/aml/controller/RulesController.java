package com.posgateway.aml.controller;


import com.fasterxml.jackson.databind.ObjectMapper;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.ai.AiRuleGeneratorService;
import com.posgateway.aml.service.rules.DroolsRulesService;
import com.posgateway.aml.service.rules.DynamicRuleConverter;
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




@RestController
@RequestMapping("/rules")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_USER')")
public class RulesController {

    private final RuleDefinitionRepository ruleRepository;
    private final AiRuleGeneratorService aiService;
    private final DroolsRulesService droolsService;
    private final DynamicRuleConverter converter;
    private final PspIsolationService pspIsolationService;
    private final UserRepository userRepository;
    

    @Autowired
    public RulesController(
            RuleDefinitionRepository ruleRepository,
            AiRuleGeneratorService aiService,
            DroolsRulesService droolsService,
            DynamicRuleConverter converter,
            PspIsolationService pspIsolationService,
            UserRepository userRepository) {
        this.ruleRepository = ruleRepository;
        this.aiService = aiService;
        this.droolsService = droolsService;
        this.converter = converter;
        this.pspIsolationService = pspIsolationService;
        this.userRepository = userRepository;
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
    public ResponseEntity<RuleDefinition> generateRule(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        RuleDefinition rule = aiService.generateRuleFromText(prompt);
        return ResponseEntity.ok(rule);
    }

    @PostMapping
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
        
        // Set creator and PSP ID
        rule.setCreatedBy(currentUser.getId());
        if (pspIsolationService.isPlatformAdministrator(currentUser)) {
            // Super admin creates rules with null pspId
            rule.setPspId(null);
        } else {
            // PSP user creates rules with their PSP ID
            Long pspId = pspIsolationService.getCurrentUserPspId();
            rule.setPspId(pspId);
        }
        
        RuleDefinition saved = ruleRepository.save(rule);
        
        // Auto-reload rules engine
        droolsService.reloadRules();
        
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<RuleDefinition>> getAllRules() {
        // HOK-40: filter by current user's PSP
        User currentUser = getCurrentUser();
        if (!pspIsolationService.isPlatformAdministrator(currentUser)) {
            Long pspId = pspIsolationService.getCurrentUserPspId();
            if (pspId != null) {
                return ResponseEntity.ok(ruleRepository.findByPspId(pspId));
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
        return ruleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reload")
    public ResponseEntity<String> reloadRulesEngine() {
        droolsService.reloadRules();
        return ResponseEntity.ok("Rules engine reloaded successfully.");
    }

    @PutMapping("/{id}")
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

        // Update rule fields
        if (rule.getName() != null) existing.setName(rule.getName());
        if (rule.getDescription() != null) existing.setDescription(rule.getDescription());
        if (rule.getRuleJson() != null) existing.setRuleJson(rule.getRuleJson());
        if (rule.getDrlContent() != null) existing.setDrlContent(rule.getDrlContent());
        if (rule.getPriority() != null) existing.setPriority(rule.getPriority());
        if (rule.getRuleType() != null) existing.setRuleType(rule.getRuleType());
        if (rule.getRuleExpression() != null) existing.setRuleExpression(rule.getRuleExpression());
        if (rule.getScore() != null) existing.setScore(rule.getScore());
        if (rule.getAction() != null) existing.setAction(rule.getAction());
        existing.setEnabled(rule.isEnabled());
        existing.setUpdatedBy(currentUser.getId());

        RuleDefinition saved = ruleRepository.save(existing);
        droolsService.reloadRules();
        
        return ResponseEntity.ok(saved);
    }

    /**
     * Enable a rule
     * POST /api/v1/rules/{id}/enable
     */
    @PostMapping("/{id}/enable")
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

        existing.setEnabled(true);
        existing.setUpdatedBy(currentUser.getId());
        RuleDefinition saved = ruleRepository.save(existing);
        droolsService.reloadRules();
        return ResponseEntity.ok(saved);
    }

    /**
     * Disable a rule
     * POST /api/v1/rules/{id}/disable
     */
    @PostMapping("/{id}/disable")
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

        existing.setEnabled(false);
        existing.setUpdatedBy(currentUser.getId());
        RuleDefinition saved = ruleRepository.save(existing);
        droolsService.reloadRules();
        return ResponseEntity.ok(saved);
    }

    /**
     * Get rule effectiveness metrics
     * GET /api/v1/rules/{id}/effectiveness
     */
    @GetMapping("/{id}/effectiveness")
    public ResponseEntity<Map<String, Object>> getRuleEffectiveness(@PathVariable Long id) {
        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

        // Calculate effectiveness metrics
        // TODO: Implement real effectiveness calculation based on rule execution history
        // For now, return mock data structure
        Map<String, Object> effectiveness = new java.util.HashMap<>();
        effectiveness.put("ruleId", rule.getId());
        effectiveness.put("ruleName", rule.getName());
        effectiveness.put("totalExecutions", 0L);
        effectiveness.put("truePositives", 0L);
        effectiveness.put("falsePositives", 0L);
        effectiveness.put("falsePositiveRate", 0.0);
        effectiveness.put("averageExecutionTime", 0.0);
        effectiveness.put("lastExecuted", null);
        
        return ResponseEntity.ok(effectiveness);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RuleDefinition existing = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

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

        ruleRepository.deleteById(id);
        droolsService.reloadRules();
        return ResponseEntity.ok().build();
    }
}

