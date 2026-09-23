package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.chargeback.ChargebackDisputeRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.MerchantScreeningResultRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.cache.FeatureCacheService;
import com.posgateway.aml.service.compliance.CashStructuringDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First tests for the feature enrichment that every SpEL rule reads. Focused on the
 * {@code amount_ending_pattern} signal, which previously described the card's PREVIOUS transaction
 * (the history query excludes the current one) rather than the transaction being evaluated — so
 * R-117 fired on the wrong amount.
 */
@ExtendWith(MockitoExtension.class)
class RuleFeatureEnrichmentServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private HighRiskCountryRepository highRiskCountryRepository;
    @Mock private ChargebackDisputeRepository chargebackDisputeRepository;
    @Mock private FeatureCacheService featureCacheService;
    @Mock private MerchantScreeningResultRepository merchantScreeningResultRepository;
    @Mock private CashStructuringDetectionService cashStructuringDetectionService;
    @Mock private com.posgateway.aml.service.analytics.BehavioralAnalyticsService behavioralAnalyticsService;

    private RuleFeatureEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new RuleFeatureEnrichmentService(transactionRepository, merchantRepository,
                highRiskCountryRepository, chargebackDisputeRepository, featureCacheService,
                merchantScreeningResultRepository, cashStructuringDetectionService,
                behavioralAnalyticsService);
    }

    /** Invoke the private enrichment directly so the test does not depend on every collaborator. */
    private Map<String, Object> amountPatternFor(Long amountCents) {
        Map<String, Object> features = new HashMap<>();
        ReflectionTestUtils.invokeMethod(service, "enrichRoundValueMetrics",
                features, null, LocalDateTime.now(), amountCents);
        return features;
    }

    @Test
    void roundAmountTripsTheEndingPattern() {
        // 1000.00 -> cents 0 -> round-number pattern
        assertEquals(Boolean.TRUE, amountPatternFor(100_000L).get("amount_ending_pattern"));
    }

    @Test
    void ninetyNineEndingTripsTheEndingPattern() {
        // 19.99 -> cents 99 -> charm-price / test-transaction pattern
        assertEquals(Boolean.TRUE, amountPatternFor(1_999L).get("amount_ending_pattern"));
    }

    @Test
    void ordinaryCentsDoNotTripTheEndingPattern() {
        assertEquals(Boolean.FALSE, amountPatternFor(1_234L).get("amount_ending_pattern"));
        assertEquals(Boolean.FALSE, amountPatternFor(50_042L).get("amount_ending_pattern"));
    }

    @Test
    void negativeAmountsUseTheAbsoluteCents() {
        // A refund of -19.99 still exhibits the same ending pattern.
        assertEquals(Boolean.TRUE, amountPatternFor(-1_999L).get("amount_ending_pattern"));
    }

    @Test
    void missingAmountLeavesTheFeatureUnset() {
        // A rule reading an unset feature evaluates false rather than throwing.
        assertFalse(amountPatternFor(null).containsKey("amount_ending_pattern"));
    }

    // --- R-170 "Anonymous Payment Screening" (was hardcoded false → the rule could never fire) ---

    private boolean anonymousHitFor(TransactionEntity txn) {
        Map<String, Object> features = new HashMap<>();
        ReflectionTestUtils.invokeMethod(service, "enrichBlacklistHits", txn, features);
        return (Boolean) features.get("anonymous_payment_screening_hit");
    }

    /** An identified, non-prepaid, non-cash payment — the baseline that must NOT trip the rule. */
    private TransactionEntity identifiedTransaction() {
        TransactionEntity txn = new TransactionEntity();
        txn.setCardType("CREDIT");
        txn.setCustomerAccountReference("ACC-1001");
        txn.setCustomerEmail("payer@example.com");
        return txn;
    }

    @Test
    void identifiedCardPaymentIsNotAnonymous() {
        assertFalse(anonymousHitFor(identifiedTransaction()));
    }

    @Test
    void prepaidInstrumentIsAnonymous() {
        TransactionEntity txn = identifiedTransaction();
        txn.setCardType("PREPAID");
        assertTrue(anonymousHitFor(txn), "a prepaid bearer instrument is an anonymity signal");
    }

    @Test
    void cashTransactionIsAnonymous() {
        TransactionEntity txn = identifiedTransaction();
        txn.setCashTransaction(true);
        assertTrue(anonymousHitFor(txn));
    }

    @Test
    void missingAllCustomerIdentificationIsAnonymous() {
        TransactionEntity txn = identifiedTransaction();
        txn.setCustomerAccountReference(null);
        txn.setCustomerEmail("   ");
        assertTrue(anonymousHitFor(txn), "no account reference and no email means an unidentified payer");
    }

    @Test
    void oneIdentifierIsEnoughToNotBeAnonymous() {
        TransactionEntity txn = identifiedTransaction();
        txn.setCustomerEmail(null);
        assertFalse(anonymousHitFor(txn), "an account reference alone still identifies the payer");
    }

    @Test
    void patternIsComputedWithoutAnyTransactionHistory() {
        // The signal describes the CURRENT transaction, so it must not depend on (or query) history.
        Map<String, Object> features = amountPatternFor(2_599L);
        assertTrue(features.containsKey("amount_ending_pattern"));
        org.mockito.Mockito.verifyNoInteractions(transactionRepository);
    }
}
