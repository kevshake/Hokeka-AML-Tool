package com.posgateway.aml.service.risk.rules;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.Transaction;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.core.RuleBuilder;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class RiskRuleDefinitions {

    public Rules getRules() {
        Rules rules = new Rules();

        // 1. High Value Transaction Rule
        rules.register(new RuleBuilder()
                .name("High Value Transaction")
                .description("Flag transactions over $10,000")
                .priority(1)
                .when(facts -> {
                    Transaction tx = facts.get("transaction");
                    return tx.getAmount().compareTo(new BigDecimal("10000")) > 0;
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("HIGH_VALUE_TRANSACTION");
                })
                .build());

        // 2. High Risk Country Rule (Simplified)
        rules.register(new RuleBuilder()
                .name("High Risk Country")
                .description("Flag transactions from high risk countries")
                .priority(2)
                .when(facts -> {
                    Transaction tx = facts.get("transaction");
                    String country = tx.getCountryCode();
                    return "NK".equals(country) || "IR".equals(country); // Example list
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("HIGH_RISK_COUNTRY");
                })
                .build());

        // 3. G2 - URL Mismatch Rule (Transaction Laundering)
        rules.register(new RuleBuilder()
                .name("URL Mismatch")
                .description("Transaction URL does not match Merchant Website")
                .priority(3)
                .when(facts -> {
                    Transaction tx = facts.get("transaction");
                    Merchant m = facts.get("merchant");
                    if (tx.getTransactionUrl() == null || m.getWebsite() == null)
                        return false;
                    return !normalizeUrl(tx.getTransactionUrl()).contains(normalizeUrl(m.getWebsite()));
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("URL_MISMATCH_SUSPECTED_LAUNDERING");
                })
                .build());

        // 4. G2 - MCC Mismatch Rule (Transaction Laundering)
        rules.register(new RuleBuilder()
                .name("MCC Mismatch")
                .description("Transaction MCC does not match Merchant MCC")
                .priority(4)
                .when(facts -> {
                    Transaction tx = facts.get("transaction");
                    Merchant m = facts.get("merchant");
                    if (tx.getMerchantCategoryCode() == null || m.getMcc() == null)
                        return false;
                    return !tx.getMerchantCategoryCode().equals(m.getMcc());
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("MCC_MISMATCH_SUSPECTED_LAUNDERING");
                })
                .build());

        // 5. Link Analysis Rule
        rules.register(new RuleBuilder()
                .name("Linked to Blocked Entity")
                .description("Merchant shares attributes with a Blocked Entity")
                .priority(1)
                .when(facts -> {
                    return facts.get("isLinkedToBlocked") != null && (Boolean) facts.get("isLinkedToBlocked");
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("LINKED_TO_BLOCKED_ENTITY");
                })
                .build());

        // 6. Behavioral Anomaly Rule
        rules.register(new RuleBuilder()
                .name("Behavioral Anomaly")
                .description("Transaction significantly deviates from merchant history")
                .priority(5)
                .when(facts -> {
                    return facts.get("isBehavioralAnomaly") != null && (Boolean) facts.get("isBehavioralAnomaly");
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("BEHAVIORAL_ANOMALY");
                })
                .build());

        // 7. Structuring (Kenya Scenario)
        rules.register(new RuleBuilder()
                .name("Structuring Suspected")
                .description("Transaction amount is just below reporting threshold or repeated round numbers")
                .priority(2)
                .when(facts -> {
                    return facts.get("isStructuringSuspected") != null && (Boolean) facts.get("isStructuringSuspected");
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("STRUCTURING_SUSPECTED");
                })
                .build());

        // 8. PEP Transaction
        rules.register(new RuleBuilder()
                .name("PEP Transaction")
                .description("Transaction involves a Politically Exposed Person")
                .priority(1)
                .when(facts -> {
                    Merchant m = facts.get("merchant");
                    return m != null && m.isPep();
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("PEP_TRANSACTION");
                })
                .build());

        // 9. Third Party Usage (Kenya Scenario)
        rules.register(new RuleBuilder()
                .name("Third Party Account Usage")
                .description("Mismatch between account holder and merchant name")
                .priority(2)
                .when(facts -> {
                    return facts.get("isThirdPartySuspected") != null && (Boolean) facts.get("isThirdPartySuspected");
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("THIRD_PARTY_USAGE_SUSPECTED");
                })
                .build());

        return rules;
    }

    private String normalizeUrl(String url) {
        return url.toLowerCase().replace("https://", "").replace("http://", "").replace("www.", "");
    }
}
