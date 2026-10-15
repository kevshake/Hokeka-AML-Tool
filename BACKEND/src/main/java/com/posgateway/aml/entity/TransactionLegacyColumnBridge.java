package com.posgateway.aml.entity;

import java.math.BigDecimal;

/**
 * Keeps {@link TransactionEntity} inserts compatible with legacy {@code transactions.amount}
 * NOT NULL columns: Flyway {@code V148__transactions_legacy_column_sync.sql} installs a
 * BEFORE INSERT trigger on databases that still carry legacy columns; this helper ensures
 * {@code amount_cents} is always populated for that trigger and for modern ingest paths.
 */
public final class TransactionLegacyColumnBridge {

    private TransactionLegacyColumnBridge() {
    }

    /**
     * Normalizes amount and regulatory boolean defaults on the entity before persistence.
     */
    public static void applyBeforePersist(TransactionEntity transaction) {
        if (transaction.getAmountCents() == null) {
            transaction.setAmountCents(0L);
        }
        if (transaction.getCurrency() == null || transaction.getCurrency().isBlank()) {
            transaction.setCurrency("USD");
        }
        if (transaction.getTxnTs() == null) {
            transaction.setTxnTs(java.time.LocalDateTime.now());
        }
        if (transaction.getPanHash() == null || transaction.getPanHash().isBlank()) {
            transaction.setPanHash("UNKNOWN");
        }
    }

    /**
     * Converts persisted cents to major units for APIs that still speak in {@code amount}.
     */
    public static BigDecimal amountMajorUnits(Long amountCents) {
        if (amountCents == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amountCents, 2);
    }
}
