package com.posgateway.aml.config;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Production Rule Seeder - Seeds high quality system rules.
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
        if (ruleRepository.count() > 50) return;

        List<RuleDefinition> rules = new ArrayList<>();

        // Transaction & Velocity Rules
        rules.add(create("R-1", "First transaction of user", "#tx.isFirstTransaction == true", "SPEL", "FLAG", 85));
        rules.add(create("R-2", "Large amount", "#tx.amount >= 10000", "SPEL", "ALERT", 80));
        rules.add(create("R-3", "Round amount", "#tx.amount % 1000 == 0", "SPEL", "HOLD", 65));
        rules.add(create("R-4", "High velocity", "#history.txCount1h >= 5", "SPEL", "ALERT", 82));
        rules.add(create("R-5", "Structuring", "#tx.amount >= 9000 && #tx.amount < 10000", "SPEL", "HOLD", 90));
        rules.add(create("R-6", "New country", "#tx.country != #history.lastCountry", "SPEL", "FLAG", 60));
        rules.add(create("R-7", "High risk country", "#tx.country in ['NG','PK','AF']", "SPEL", "HOLD", 75));
        rules.add(create("R-8", "Device change", "#tx.deviceFingerprint != #history.lastDevice", "SPEL", "ALERT", 70));
        rules.add(create("R-9", "IP change short time", "#tx.ipAddress != #history.lastIp && #history.timeSinceLastTxn < 300", "SPEL", "HOLD", 78));
        rules.add(create("R-10", "Night hours", "#tx.hour >= 2 && #tx.hour <= 5", "SPEL", "FLAG", 55));

        // Advanced & Screening Rules
        rules.add(create("R-11", "Name similarity high", "#merchant.nameLevenshtein > 0.85", "SPEL", "ALERT", 80));
        rules.add(create("R-12", "Multiple cards same device", "#history.uniqueCards24h >= 3", "SPEL", "HOLD", 85));
        rules.add(create("R-13", "High diversity", "#history.merchantDiversity7d > 8", "SPEL", "ALERT", 68));
        rules.add(create("R-14", "Sanctions hit", "#merchant.sanctionsHit == true", "SPEL", "SUSPEND", 100));
        rules.add(create("R-15", "PEP beneficial owner", "#ubo.pepHit == true", "SPEL", "HOLD", 95));

        // Add more rules as needed up to R-170

        ruleRepository.saveAll(rules);
    }

    private RuleDefinition create(String code, String desc, String expr, String type, String action, int priority) {
        RuleDefinition r = new RuleDefinition();
        r.setName(code);
        r.setDescription(desc);
        r.setRuleType(type);
        r.setRuleExpression(expr);
        r.setAction(action);
        r.setPriority(priority);
        r.setEnabled(true);
        r.setPspId(null);
        r.setIsSystemRule(true);
        r.setOwnerType("SYSTEM");
        return r;
    }
}