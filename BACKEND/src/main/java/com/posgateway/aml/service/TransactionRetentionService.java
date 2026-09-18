package com.posgateway.aml.service;

import com.posgateway.aml.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Enforces the transaction-data retention policy: transactions (and their feature/alert rows) that
 * are older than the retention window are flushed from the operational database.
 *
 * <p><b>Compliance guard:</b> a transaction that is linked to a SAR ({@code sar_transactions}) or a
 * compliance case ({@code case_transactions}) is NOT purged even when it is past the window — those
 * are under a regulatory hold and must be retained until the investigation is closed. The purge
 * query excludes them, which also keeps the delete free of foreign-key violations.
 *
 * <p>Work is done in bounded batches, each in its own transaction, so a large backlog cannot hold a
 * single long transaction or lock the table; whatever is not cleared in one run is picked up by the
 * next.
 */
@Service
public class TransactionRetentionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionRetentionService.class);

    private final TransactionRepository transactionRepository;

    /** Self-reference so per-batch {@link #purgeBatch} calls go through the transactional proxy. */
    @Autowired
    @Lazy
    private TransactionRetentionService self;

    @Value("${transaction.retention.months:6}")
    private int retentionMonths;

    @Value("${transaction.retention.batch-size:1000}")
    private int batchSize;

    @Value("${transaction.retention.max-batches-per-run:200}")
    private int maxBatchesPerRun;

    public TransactionRetentionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /** Daily at 02:30 by default. */
    @Scheduled(cron = "${transaction.retention.cron:0 30 2 * * *}")
    public void purgeOldTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(retentionMonths);
        long totalDeleted = 0;
        int batches = 0;
        while (batches < maxBatchesPerRun) {
            int deleted = self.purgeBatch(cutoff);
            if (deleted == 0) {
                break;
            }
            totalDeleted += deleted;
            batches++;
        }
        if (totalDeleted > 0) {
            log.info("Transaction retention purge: deleted {} transactions older than {} in {} batch(es)",
                    totalDeleted, cutoff, batches);
        }
    }

    /**
     * Purge one batch atomically: pick purgeable ids, delete their child feature/alert rows, then
     * the transactions themselves. Returns the number of transactions deleted (0 when none remain).
     */
    @Transactional
    public int purgeBatch(LocalDateTime cutoff) {
        List<Long> ids = transactionRepository.findPurgeableTxnIds(cutoff, batchSize);
        if (ids.isEmpty()) {
            return 0;
        }
        transactionRepository.deleteFeaturesByTxnIds(ids);
        transactionRepository.deleteAlertsByTxnIds(ids);
        return transactionRepository.deleteTransactionsByIds(ids);
    }
}
