package com.posgateway.aml.rules;

import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.rules.DroolsRulesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the feature-key registry and proves the 24h PAN-sum bug (B-2) is fixed: the fact-builder
 * reads the key the enrichment actually produces, so the fact reflects real usage instead of 0.
 */
@ExtendWith(MockitoExtension.class)
class RuleFeatureKeysTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private RuleDefinitionRepository ruleRepository;

    @Test
    void factBuilderReadsTheProducedPanSumKey() {
        DroolsRulesService service = new DroolsRulesService(redisTemplate, ruleRepository);
        Map<String, Object> features = new HashMap<>();
        features.put(RuleFeatureKeys.PAN_AMOUNT_SUM_24H, 5000.0); // enrichment writes this key
        TransactionFact fact = service.buildTransactionFactPublic(1L, features, 0.5);
        assertEquals(5000.0, fact.getPanAmountSum24h(), 1e-9,
                "the fact must reflect the enrichment-produced 24h PAN sum, not stay at 0");
    }

    @Test
    void registryDropsThePhantomKeyAndKeepsTheRealOne() {
        assertFalse(RuleFeatureKeys.FACT_BUILDER_KEYS.contains("pan_txn_amount_sum_24h"),
                "the non-existent key must not be in the registry");
        assertTrue(RuleFeatureKeys.FACT_BUILDER_KEYS.contains(RuleFeatureKeys.PAN_AMOUNT_SUM_24H));
    }
}
