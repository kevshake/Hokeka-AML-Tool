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
 * Seeds high-quality rules for AML/Fraud detection.
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
        if (ruleRepository.count() > 150) return;

        List<RuleDefinition> rules = new ArrayList<>();

        // Transaction & Velocity Rules (R-1 to R-40)
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

        // Advanced Rules (R-11 to R-60)
        rules.add(create("R-11", "High name similarity", "#merchant.nameLevenshtein > 0.85", "SPEL", "ALERT", 80));
        rules.add(create("R-12", "Multiple cards same device", "#history.uniqueCards24h >= 3", "SPEL", "HOLD", 85));
        rules.add(create("R-13", "High merchant diversity", "#history.merchantDiversity7d > 8", "SPEL", "ALERT", 68));
        rules.add(create("R-14", "Sanctions hit on merchant", "#merchant.sanctionsHit == true", "SPEL", "SUSPEND", 100));
        rules.add(create("R-15", "PEP on beneficial owner", "#ubo.pepHit == true", "SPEL", "HOLD", 95));
        rules.add(create("R-16", "High ML risk score", "#ml.score > 0.85", "SPEL", "HOLD", 88));
        rules.add(create("R-17", "Rapid successive transactions", "#history.timeSinceLastTxn < 60", "SPEL", "ALERT", 72));
        rules.add(create("R-18", "Unusual merchant category", "#tx.mcc not in #history.commonMccs", "SPEL", "FLAG", 65));
        rules.add(create("R-19", "High amount for merchant", "#tx.amount > #history.avgAmount * 3", "SPEL", "ALERT", 78));
        rules.add(create("R-20", "New IP address", "#tx.ipAddress not in #history.knownIps", "SPEL", "FLAG", 60));

        // Screening & Compliance (R-21 to R-80)
        rules.add(create("R-21", "Sanctions hit on counterparty", "#counterparty.sanctionsHit == true", "SPEL", "SUSPEND", 100));
        rules.add(create("R-22", "PEP screening hit", "#counterparty.pepHit == true", "SPEL", "HOLD", 92));
        rules.add(create("R-23", "Adverse media hit", "#merchant.adverseMediaHit == true", "SPEL", "HOLD", 85));
        rules.add(create("R-24", "High risk score from ML model", "#ml.riskScore > 0.8", "SPEL", "HOLD", 80));
        rules.add(create("R-25", "Velocity spike on merchant", "#history.merchantVelocity1h > 10", "SPEL", "ALERT", 75));

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
        rule.setSystemManaged(true);
        rule.setParameters("[]");
        return rule;
    }
}