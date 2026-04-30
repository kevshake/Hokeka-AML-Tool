package com.posgateway.aml.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Connection Cleanup Service
 * Safely handles connection cleanup and resource release after transaction completion
 * Reduces tail latency by quickly releasing resources
 */
@Service
public class ConnectionCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionCleanupService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final AtomicLong completedTransactions = new AtomicLong(0);
    private final AtomicLong cleanedUpConnections = new AtomicLong(0);

    @Autowired
    public ConnectionCleanupService() {
        // Constructor
    }

    /**
     * Cleanup resources after transaction completion
     * Called after each transaction is processed
     * 
     * @param txnId Transaction ID
     */
    public void cleanupAfterTransaction(Long txnId) {
        try {
            // Clear EntityManager cache for this transaction
            if (entityManager != null) {
                entityManager.clear(); // Clears persistence context cache
            }

            // Hint GC to clean up (non-blocking)
            System.gc(); // Suggest garbage collection (optional, may not be needed)

            completedTransactions.incrementAndGet();
            
            logger.debug("Cleaned up resources for transaction: {}", txnId);

        } catch (Exception e) {
            logger.warn("Error during cleanup for transaction {}: {}", txnId, e.getMessage());
        }
    }

    /**
     * Periodic cleanup of expired connections and resources
     * Runs every 30 seconds
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    @Transactional
    public void periodicCleanup() {
        try {
            // Clear EntityManager cache periodically
            if (entityManager != null) {
                entityManager.clear();
            }

            cleanedUpConnections.incrementAndGet();
            
            long after = cleanedUpConnections.get();
            
            if (after % 10 == 0) { // Log every 10th cleanup
                logger.debug("Periodic cleanup completed. Total cleanups: {}", after);
            }

        } catch (Exception e) {
            logger.warn("Error during periodic cleanup: {}", e.getMessage());
        }
    }

    /**
     * Get cleanup statistics
     */
    public CleanupStats getCleanupStats() {
        CleanupStats stats = new CleanupStats();
        stats.setCompletedTransactions(completedTransactions.get());
        stats.setCleanedUpConnections(cleanedUpConnections.get());
        return stats;
    }

    /**
     * Cleanup Statistics
     */
    public static class CleanupStats {
        private long completedTransactions;
        private long cleanedUpConnections;

        public long getCompletedTransactions() {
            return completedTransactions;
        }

        public void setCompletedTransactions(long completedTransactions) {
            this.completedTransactions = completedTransactions;
        }

        public long getCleanedUpConnections() {
            return cleanedUpConnections;
        }

        public void setCleanedUpConnections(long cleanedUpConnections) {
            this.cleanedUpConnections = cleanedUpConnections;
        }
    }
}

