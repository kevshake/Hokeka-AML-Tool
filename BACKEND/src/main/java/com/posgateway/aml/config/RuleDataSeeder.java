package com.posgateway.aml.config;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Production-grade system rule seeder.
 * Seeds high-quality rules used across Test and Production.
 */
@Component
public class RuleDataSeeder {

    private final RuleDefinitionRepository ruleRepository;

    public RuleDataSeeder(RuleDefinitionRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @PostConstruct
    @Transactional
    public void seedDefaultRules() {
        if (ruleRepository.count() > 40) return;

        List<RuleDefinition> rules = new ArrayList<>();

        // R-1 to R-20: Core transaction rules
        rules.add(create("R-1", "First transaction of user", "#tx.isFirstTransaction == true", "SPEL", "FLAG", 85));
        rules.add(create("R-2", "Large amount (>=10000)", "#tx.amount >= 10000", "SPEL", "ALERT", 80));
        rules.add(create("R-3", "Round amount pattern", "#tx.amount % 1000 == 0", "SPEL", "HOLD", 65));
        rules.add(create("R-4", "High velocity (5+ txns/1h)", "#history.txCount1h >= 5", "SPEL", "ALERT", 82));
        rules.add(create("R-5", "Structuring pattern", "#tx.amount >= 9000 && #tx.amount < 10000", "SPEL", "HOLD", 90));
        rules.add(create("R-6", "New country activity", "#tx.country != #history.lastCountry", "SPEL", "FLAG", 60));
        rules.add(create("R-7", "High-risk country", "#tx.country in ['NG','PK','AF','RU']", "SPEL", "HOLD", 75));
        rules.add(create("R-8", "Device fingerprint change", "#tx.deviceFingerprint != #history.lastDevice", "SPEL", "ALERT", 70));
        rules.add(create("R-9", "IP change in short time", "#tx.ipAddress != #history.lastIp && #history.timeSinceLastTxn < 300", "SPEL", "HOLD", 78));
        rules.add(create("R-10", "Night transaction (2-5am)", "#tx.hour >= 2 && #tx.hour <= 5", "SPEL", "FLAG", 55));

        // R-11 to R-30: Advanced rules
        rules.add(create("R-11", "High name similarity", "#merchant.nameLevenshtein > 0.85", "SPEL", "ALERT", 80));
        rules.add(create("R-12", "Multiple cards same device", "#history.uniqueCards24h >= 3", "SPEL", "HOLD", 85));
        rules.add(create("R-13", "High merchant diversity", "#history.merchantDiversity7d > 8", "SPEL", "ALERT", 68));
        rules.add(create("R-14", "Sanctions hit on merchant", "#merchant.sanctionsHit == true", "SPEL", "SUSPEND", 100));
        rules.add(create("R-15", "PEP on beneficial owner", "#ubo.pepHit == true", "SPEL", "HOLD", 95));

        // Add more rules here in future iterations (up to R-170)

        ruleRepository.saveAll(rules);
    }

    private RuleDefinition create(String code, String description, String expression,
                                  String ruleType, String action, int priority) {
        RuleDefinition rule = new RuleDefinition();
        rule.setName(code);
        rule.setDescription(description);
        rule.setRuleType(ruleType);
        rule.setRuleExpression(expression);
        rule.setAction(action);
        rule.setPriority(priority);
        rule.setEnabled(true);
        rule.setPspId(null);
        rule.setIsSystemRule(true);
        rule.setOwnerType("SYSTEM");
        return rule;
    }
}