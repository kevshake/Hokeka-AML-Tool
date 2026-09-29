package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.enrichment.IpReputationService;
import com.posgateway.aml.service.rules.RuleFeatureEnrichmentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OptimizedFeatureExtractionServiceTest {

    @Test
    void extractFeaturesAppliesRuleEnrichmentLikeTheStandardPath() {
        RuleFeatureEnrichmentService enrichment = mock(RuleFeatureEnrichmentService.class);
        IpReputationService ipReputation = new IpReputationService();
        ReflectionTestUtils.setField(ipReputation, "enabled", true);
        ipReputation.refreshRanges();
        OptimizedFeatureExtractionService service = new OptimizedFeatureExtractionService(
                mock(TransactionRepository.class),
                new ObjectMapper(),
                ipReputation,
                enrichment);
        ReflectionTestUtils.setField(service, "parallelEnabled", false);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(7L);
        transaction.setTxnTs(LocalDateTime.of(2026, 9, 29, 8, 0));
        transaction.setAmountCents(2500L);
        transaction.setCurrency("KES");
        transaction.setMerchantId("11");

        Map<String, Object> features = service.extractFeatures(transaction);
        verify(enrichment).enrich(same(transaction), same(features));

        Map<String, Object> reuse = new HashMap<>();
        service.extractFeaturesInto(transaction, reuse);
        verify(enrichment).enrich(same(transaction), same(reuse));
    }
}
