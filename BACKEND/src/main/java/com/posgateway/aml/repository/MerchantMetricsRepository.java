package com.posgateway.aml.repository;

import com.posgateway.aml.model.MerchantMetrics;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/** Builds rolling card-scheme metrics from durable transaction and dispute records. */
@Repository
public class MerchantMetricsRepository {

    private static final int LOOKBACK_DAYS = 30;

    @PersistenceContext
    private EntityManager entityManager;

    public MerchantMetrics load30DayMetrics(String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("merchantId is required");
        }

        LocalDateTime since = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        Query transactionQuery = entityManager.createNativeQuery(
                "SELECT " +
                "  COUNT(*) AS total_tx, " +
                "  COUNT(*) FILTER (WHERE decision IN ('BLOCK','DECLINED','REJECTED')) AS fraud_tx, " +
                "  COUNT(*) FILTER (WHERE bill_classification_code = 'CHARGEBACK') AS cb_count, " +
                "  COALESCE(SUM(amount_cents) FILTER (WHERE decision IN ('BLOCK','DECLINED','REJECTED')), 0) AS fraud_amount, " +
                "  COALESCE(SUM(amount_cents) FILTER (WHERE bill_classification_code = 'CHARGEBACK'), 0) AS cb_amount " +
                "FROM transactions WHERE merchant_id = :merchantId AND txn_ts >= :since");
        transactionQuery.setParameter("merchantId", merchantId);
        transactionQuery.setParameter("since", since);
        Object[] transactionRow = (Object[]) transactionQuery.getSingleResult();

        long disputeCount = 0L;
        long disputeAmount = 0L;
        Long numericMerchantId = parseLong(merchantId);
        if (numericMerchantId != null) {
            Query disputeQuery = entityManager.createNativeQuery(
                    "SELECT COUNT(*), COALESCE(SUM(ROUND(case_amount * 100)), 0) " +
                    "FROM chargeback_disputes " +
                    "WHERE merchant_id = :merchantId AND created_at >= :since");
            disputeQuery.setParameter("merchantId", numericMerchantId);
            disputeQuery.setParameter("since", since);
            Object[] disputeRow = (Object[]) disputeQuery.getSingleResult();
            disputeCount = asLong(disputeRow[0]);
            disputeAmount = asLong(disputeRow[1]);
        }

        return new MerchantMetrics(
                asLong(transactionRow[0]),
                asLong(transactionRow[1]),
                asLong(transactionRow[2]) + disputeCount,
                asLong(transactionRow[3]),
                asLong(transactionRow[4]) + disputeAmount);
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static long asLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }
}
