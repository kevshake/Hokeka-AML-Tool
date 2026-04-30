package com.posgateway.aml.service.risk;



import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.service.risk.rules.RiskRuleDefinitions;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// @RequiredArgsConstructor removed
@Service
public class RiskRulesEngine {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RiskRulesEngine.class);

    private final RiskRuleDefinitions ruleDefinitions;

    public RiskRulesEngine(RiskRuleDefinitions ruleDefinitions) {
        this.ruleDefinitions = ruleDefinitions;
    }


    public List<String> evaluateRisk(Transaction transaction, Merchant merchant) {
        return evaluateRisk(transaction, merchant, new java.util.HashMap<>());
    }

    public List<String> evaluateRisk(Transaction transaction, Merchant merchant,
            java.util.Map<String, Object> extraFacts) {
        log.debug("Evaluating risk rules for transaction: {}", transaction.getTransactionId());

        RulesEngine rulesEngine = new DefaultRulesEngine();
        Facts facts = new Facts();
        facts.put("transaction", transaction);
        facts.put("merchant", merchant);

        // Add extra facts
        if (extraFacts != null) {
            extraFacts.forEach(facts::put);
        }

        // Output list to collect triggered rule names/reasons
        List<String> triggeredRules = new ArrayList<>();
        facts.put("triggeredRules", triggeredRules);

        Rules rules = ruleDefinitions.getRules();
        rulesEngine.fire(rules, facts);

        if (!triggeredRules.isEmpty()) {
            log.warn("Transaction {} triggered {} risk rules: {}",
                    transaction.getTransactionId(), triggeredRules.size(), triggeredRules);
        }

        return triggeredRules;
    }
}
