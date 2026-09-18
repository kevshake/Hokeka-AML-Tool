package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.TransactionFeatures;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.TransactionFeaturesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Batch Scoring Service — post-transaction (settled) monitoring.
 *
 * <p>Runs daily to score transactions that never got a real-time score and routes
 * each through the {@link DecisionEngine} so alerts and cases are raised from settled
 * activity (this is the post-transaction leg of AML monitoring). Also persists the
 * feature rows that the offline training store consumes. It does NOT itself train a model.
 */
@Service
public class BatchScoringService {

    private static final Logger logger = LoggerFactory.getLogger(BatchScoringService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionFeaturesRepository featuresRepository;
    private final FeatureExtractionService featureExtractionService;
    private final ScoringService scoringService;
    private final DecisionEngine decisionEngine;
    private final ObjectMapper objectMapper;

    public BatchScoringService(TransactionRepository transactionRepository,
            TransactionFeaturesRepository featuresRepository,
            FeatureExtractionService featureExtractionService,
            ScoringService scoringService,
            DecisionEngine decisionEngine,
            ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.featuresRepository = featuresRepository;
        this.featureExtractionService = featureExtractionService;
        this.scoringService = scoringService;
        this.decisionEngine = decisionEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * Batch score transactions from yesterday
     * Scheduled to run daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void batchScoreYesterdayTransactions() {
        logger.info("Starting batch scoring for yesterday's transactions");

        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        LocalDateTime startOfDay = yesterday.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = yesterday.toLocalDate().atTime(23, 59, 59);

        List<TransactionEntity> transactions =
                transactionRepository.findByTxnTsBetween(startOfDay, endOfDay);

        logger.info("Found {} transactions to batch score", transactions.size());

        int scored = 0;
        int skipped = 0;
        int errors = 0;

        for (TransactionEntity transaction : transactions) {
            try {
                // Check if already scored
                TransactionFeatures existing = featuresRepository.findByTxnId(transaction.getTxnId());
                if (existing != null && existing.getScore() != null) {
                    skipped++;
                    continue;
                }

                // Extract features
                Map<String, Object> features = featureExtractionService.extractFeatures(transaction);

                // Score transaction
                ScoringService.ScoringResult result = scoringService.scoreTransaction(
                        transaction.getTxnId(), features);

                if (result.getScore() != null) {
                    // Route through the decision engine so settled-transaction monitoring
                    // raises alerts/cases (and persists features + decision), instead of
                    // silently saving a score with no downstream action. Forward the rule
                    // outcomes (riskDetails) so REVIEW/ALERT rules and SAR/CTR flags apply.
                    decisionEngine.evaluate(transaction, result.getScore(), features,
                            result.getLatencyMs(), result.getRiskDetails());
                } else {
                    // No score available (scoring path unavailable) — persist the features
                    // we did compute so they are not lost.
                    saveTransactionFeatures(transaction, features, null, "BATCH");
                }

                scored++;

                if (scored % 100 == 0) {
                    logger.info("Batch scored {}/{} transactions", scored, transactions.size());
                }

            } catch (Exception e) {
                errors++;
                logger.error("Error batch scoring transaction {}: {}",
                        transaction.getTxnId(), e.getMessage());
            }
        }

        logger.info("Batch scoring completed: scored={}, skipped={}, errors={}",
                scored, skipped, errors);
    }

    /**
     * Backfill features for transactions without features
     */
    @Transactional
    public int backfillFeatures(int limit) {
        logger.info("Backfilling features for transactions without features (limit={})", limit);

        // Single DB-side query returning transactions lacking a features row (or with
        // null feature JSON), limited in the database — avoids the old full-table load
        // + per-row featuresRepository lookup (N+1).
        List<TransactionEntity> transactions = transactionRepository.findTransactionsMissingFeatures(
                org.springframework.data.domain.PageRequest.of(0, limit));

        int processed = 0;
        for (TransactionEntity transaction : transactions) {
            try {
                Map<String, Object> features = featureExtractionService.extractFeatures(transaction);
                saveTransactionFeatures(transaction, features, null, "BACKFILL");
                processed++;
            } catch (Exception e) {
                logger.error("Error backfilling features for transaction {}: {}",
                        transaction.getTxnId(), e.getMessage());
            }
        }

        logger.info("Backfilled features for {}/{} transactions", processed, transactions.size());
        return processed;
    }

    private void saveTransactionFeatures(TransactionEntity transaction,
            Map<String, Object> features,
            Double score,
            String actionTaken) {
        try {
            TransactionFeatures txnFeatures = featuresRepository.findByTxnId(transaction.getTxnId());
            if (txnFeatures == null) {
                txnFeatures = new TransactionFeatures();
                txnFeatures.setTxnId(transaction.getTxnId());
            }

            // Store features as JSON using the injected ObjectMapper
            String featureJson = objectMapper.writeValueAsString(features);
            txnFeatures.setFeatureJson(featureJson);

            if (score != null) {
                txnFeatures.setScore(score);
            }
            txnFeatures.setActionTaken(actionTaken);

            featuresRepository.save(txnFeatures);
        } catch (Exception e) {
            logger.error("Error saving transaction features for {}: {}",
                    transaction.getTxnId(), e.getMessage());
        }
    }
}
