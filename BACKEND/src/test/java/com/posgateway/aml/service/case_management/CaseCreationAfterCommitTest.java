package com.posgateway.aml.service.case_management;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression (sim run 2026-10-16): async case enrichment was started inside the case-creating
 * transaction and raced the insert (fk_case_transactions_case / fk_case_entities_case violations).
 * It now runs only after commit.
 */
class CaseCreationAfterCommitTest {

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void insideATransactionTheTaskWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();

        CaseCreationService.runAfterCommit(runs::incrementAndGet);
        assertThat(runs).hasValue(0);

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        assertThat(runs).hasValue(1);
    }

    @Test
    void rolledBackTransactionNeverRunsTheTask() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();
        CaseCreationService.runAfterCommit(runs::incrementAndGet);
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        assertThat(runs).hasValue(0);
    }

    @Test
    void withoutATransactionTheTaskRunsImmediately() {
        AtomicInteger runs = new AtomicInteger();
        CaseCreationService.runAfterCommit(runs::incrementAndGet);
        assertThat(runs).hasValue(1);
    }
}
