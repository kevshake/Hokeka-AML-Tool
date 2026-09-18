package com.posgateway.aml.service.risk.rules;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.Transaction;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.core.RuleBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class RiskRuleDefinitions {

    private final BigDecimal highValueThreshold;
    private final Set<String> highRiskMccs;

    public RiskRuleDefinitions(
            @Value("${risk.transaction.high-value-threshold:10000}") BigDecimal highValueThreshold,
            @Value("${risk.mcc.high_risk:6211,7995,7273,5993,6051}") String highRiskMccs) {
        this.highValueThreshold = highValueThreshold;
        this.highRiskMccs = Arrays.stream(highRiskMccs.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Rules getRules() {
        Rules rules = new Rules();

        // 1. High Value Transaction Rule
        rules.register(new RuleBuilder()
                .name("High Value Transaction")
                .description("Flag transactions over the configured high-value risk threshold")
                .priority(1)
                .when(facts -> {
                    Transaction tx = facts.get("transaction");
                    return tx != null && tx.getAmount() != null
                            && tx.getAmount().compareTo(highValueThreshold) > 0;
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("HIGH_VALUE_TRANSACTION");
                })
                .build());

        // 2. High Risk Country Rule
        rules.register(new RuleBuilder()
                .name("High Risk Country")
                .description("Flag transactions from high risk countries")
                .priority(2)
                .when(facts -> {
                    return Boolean.TRUE.equals(facts.get("isHighRiskCountry"));
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

        // 10. High Risk Industry (MCC based)
        rules.register(new RuleBuilder()
                .name("High Risk Industry")
                .description("Merchant belongs to a high risk category (e.g., Casinos, Crypto)")
                .priority(3)
                .when(facts -> {
                    Merchant m = facts.get("merchant");
                    if (m == null || m.getMcc() == null)
                        return false;
                    String mcc = m.getMcc();
                    return highRiskMccs.contains(mcc);
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("HIGH_RISK_INDUSTRY");
                })
                .build());

        // 11. Sanctions Match
        rules.register(new RuleBuilder()
                .name("Sanctions Match")
                .description("Transaction or Merchant matches a restricted sanctions list")
                .priority(1)
                .when(facts -> {
                    return facts.get("isSanctionsMatch") != null && (Boolean) facts.get("isSanctionsMatch");
                })
                .then(facts -> {
                    List<String> triggered = facts.get("triggeredRules");
                    triggered.add("SANCTIONS_MATCH");
                })
                .build());

        return rules;
    }

    private String normalizeUrl(String url) {
        return url.toLowerCase().replace("https://", "").replace("http://", "").replace("www.", "");
    }
}
