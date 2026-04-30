package com.posgateway.aml.service;

import com.posgateway.aml.entity.TransactionFeatures;
import com.posgateway.aml.repository.TransactionFeaturesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Feedback Labeling Service
 * Allows investigators to label transactions (fraud/not fraud) for model retraining
 * Labels flow back to training database for nightly retrain
 */
@Service
public class FeedbackLabelingService {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackLabelingService.class);

    private final TransactionFeaturesRepository featuresRepository;

    @Autowired
    public FeedbackLabelingService(TransactionFeaturesRepository featuresRepository) {
        this.featuresRepository = featuresRepository;
    }

    /**
     * Label a transaction as fraud or not fraud
     * 
     * @param txnId Transaction ID
     * @param label 1 = fraud, 0 = good, null = unknown
     * @param investigator Investigator who labeled the transaction
     * @param notes Optional notes about the labeling decision
     * @return Updated transaction features
     */
    @Transactional
    public TransactionFeatures labelTransaction(Long txnId, Short label, String investigator, String notes) {
        logger.info("Labeling transaction {} as {} by investigator {}", txnId, label, investigator);

        TransactionFeatures features = featuresRepository.findByTxnId(txnId);
        
        if (features == null) {
            throw new IllegalArgumentException("Transaction features not found for txnId: " + txnId);
        }

        // Validate label value - use switch-like pattern for better performance
        if (label != null) {
            short labelValue = label.shortValue();
            if (labelValue != 0 && labelValue != 1) {
                throw new IllegalArgumentException("Label must be 0 (good), 1 (fraud), or null (unknown)");
            }
        }

        features.setLabel(label);
        features.setScoredAt(LocalDateTime.now()); // Update timestamp

        TransactionFeatures saved = featuresRepository.save(features);
        
        // Use switch for better performance
        String labelDescription = switch (label != null ? label.shortValue() : -1) {
            case 0 -> "good";
            case 1 -> "fraud";
            default -> "unknown";
        };
        logger.info("Transaction {} labeled as {} by {}", txnId, labelDescription, investigator);
        
        return saved;
    }

    /**
     * Label multiple transactions in batch
     * 
     * @param labels Map of transaction ID to label
     * @param investigator Investigator who labeled the transactions
     * @return Number of transactions labeled
     */
    @Transactional
    public int labelTransactionsBatch(java.util.Map<Long, Short> labels, String investigator) {
        logger.info("Batch labeling {} transactions by investigator {}", labels.size(), investigator);

        int count = 0;
        for (java.util.Map.Entry<Long, Short> entry : labels.entrySet()) {
            try {
                labelTransaction(entry.getKey(), entry.getValue(), investigator, null);
                count++;
            } catch (Exception e) {
                logger.warn("Failed to label transaction {}: {}", entry.getKey(), e.getMessage());
            }
        }

        logger.info("Successfully labeled {}/{} transactions", count, labels.size());
        return count;
    }

    /**
     * Get all labeled transactions for training
     * 
     * @return List of labeled transaction features
     */
    public List<TransactionFeatures> getLabeledTransactions() {
        return featuresRepository.findLabeledTransactions();
    }

    /**
     * Get unlabeled transactions that need review
     * 
     * @param limit Maximum number of transactions to return
     * @return List of unlabeled transaction features
     */
    public List<TransactionFeatures> getUnlabeledTransactions(int limit) {
        List<TransactionFeatures> allFeatures = featuresRepository.findAll();
        return allFeatures.stream()
            .filter(tf -> tf.getLabel() == null)
            .limit(limit)
            .toList();
    }

    /**
     * Get statistics about labeling
     * 
     * @return Labeling statistics
     */
    public LabelingStatistics getLabelingStatistics() {
        List<TransactionFeatures> allFeatures = featuresRepository.findAll();
        
        // Optimize stream operations - cache label checks
        long total = allFeatures.size();
        long labeled = 0;
        long fraud = 0;
        long good = 0;
        
        // Single pass through features for better performance
        for (TransactionFeatures tf : allFeatures) {
            Short label = tf.getLabel();
            if (label != null) {
                labeled++;
                short labelValue = label.shortValue();
                if (labelValue == 1) {
                    fraud++;
                } else if (labelValue == 0) {
                    good++;
                }
            }
        }
        
        long unlabeled = total - labeled;

        LabelingStatistics stats = new LabelingStatistics();
        stats.setTotalTransactions(total);
        stats.setLabeledTransactions(labeled);
        stats.setFraudTransactions(fraud);
        stats.setGoodTransactions(good);
        stats.setUnlabeledTransactions(unlabeled);
        stats.setLabelingRate(total > 0 ? (double) labeled / total : 0.0);

        return stats;
    }

    /**
     * Labeling Statistics DTO
     */
    public static class LabelingStatistics {
        private long totalTransactions;
        private long labeledTransactions;
        private long fraudTransactions;
        private long goodTransactions;
        private long unlabeledTransactions;
        private double labelingRate;

        // Getters and Setters
        public long getTotalTransactions() {
            return totalTransactions;
        }

        public void setTotalTransactions(long totalTransactions) {
            this.totalTransactions = totalTransactions;
        }

        public long getLabeledTransactions() {
            return labeledTransactions;
        }

        public void setLabeledTransactions(long labeledTransactions) {
            this.labeledTransactions = labeledTransactions;
        }

        public long getFraudTransactions() {
            return fraudTransactions;
        }

        public void setFraudTransactions(long fraudTransactions) {
            this.fraudTransactions = fraudTransactions;
        }

        public long getGoodTransactions() {
            return goodTransactions;
        }

        public void setGoodTransactions(long goodTransactions) {
            this.goodTransactions = goodTransactions;
        }

        public long getUnlabeledTransactions() {
            return unlabeledTransactions;
        }

        public void setUnlabeledTransactions(long unlabeledTransactions) {
            this.unlabeledTransactions = unlabeledTransactions;
        }

        public double getLabelingRate() {
            return labelingRate;
        }

        public void setLabelingRate(double labelingRate) {
            this.labelingRate = labelingRate;
        }
    }
}

