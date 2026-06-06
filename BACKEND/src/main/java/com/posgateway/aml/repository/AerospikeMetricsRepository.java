package com.posgateway.aml.repository;

import com.posgateway.aml.model.MerchantMetrics;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 30-day merchant metrics aggregator.
 *
 * <p>Class name is historical (this used to be backed by Aerospike); the
 * implementation now reads directly from the {@code transactions} Postgres
 * table. The numbers feed the scheme-monitoring report and the VFMP/HECM
 * simulators which classify merchants on rolling fraud / chargeback ratios.
 *
 * <p>Definitions:
 * <ul>
 *   <li>{@code totalTx}          — all transactions in the window</li>
 *   <li>{@code fraudTx}          — transactions marked {@code decision='DECLINED'}
 *       (the closest signal we persist for "blocked as fraud")</li>
 *   <li>{@code chargebackCount}  — transactions with
 *       {@code bill_classification_code='CHARGEBACK'}</li>
 *   <li>{@code fraudAmount}      — sum of {@code amount_cents} across the
 *       fraud subset</li>
 *   <li>{@code chargebackAmount} — sum of {@code amount_cents} across the
 *       chargeback subset</li>
 * </ul>
 *
 * <p>Increments via {@link #incrementCounters} are no longer needed —
 * Micrometer counters in {@link com.posgateway.aml.service.PrometheusMetricsService}
 * cover the per-event observability story.
 */
@Repository
public class AerospikeMetricsRepository {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeMetricsRepository.class);
    private static final int LOOKBACK_DAYS = 30;

    @PersistenceContext
    private EntityManager em;

    /**
     * Aggregate the last 30 days of activity for {@code merchantId} from
     * {@code transactions}. Returns an empty {@link MerchantMetrics} when the
     * merchant has no rows in the window — callers handle "no data".
     */
    public MerchantMetrics load30DayMetrics(String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            return new MerchantMetrics();
        }
        LocalDateTime since = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        try {
            Query q = em.createNativeQuery(
                    "SELECT " +
                    "  COUNT(*)                                                                AS total_tx, " +
                    "  COUNT(*) FILTER (WHERE decision = 'DECLINED')                           AS fraud_tx, " +
                    "  COUNT(*) FILTER (WHERE bill_classification_code = 'CHARGEBACK')         AS cb_count, " +
                    "  COALESCE(SUM(amount_cents) FILTER (WHERE decision = 'DECLINED'), 0)     AS fraud_amount, " +
                    "  COALESCE(SUM(amount_cents) FILTER (WHERE bill_classification_code = 'CHARGEBACK'), 0) AS cb_amount " +
                    "FROM transactions " +
                    "WHERE merchant_id = :mid AND txn_ts >= :since");
            q.setParameter("mid", merchantId);
            q.setParameter("since", since);
            Object[] row = (Object[]) q.getSingleResult();
            return new MerchantMetrics(
                    asLong(row[0]),
                    asLong(row[1]),
                    asLong(row[2]),
                    asLong(row[3]),
                    asLong(row[4]));
        } catch (Exception e) {
            logger.warn("load30DayMetrics failed for merchant {}: {}", merchantId, e.getMessage());
            return new MerchantMetrics();
        }
    }

    /**
     * No-op. Live transaction writes already populate the aggregation source
     * columns; per-event metrics are emitted by Micrometer counters elsewhere.
     * Kept for callers that haven't migrated to the new instrumentation API.
     */
    public void incrementCounters(String merchantId, boolean isFraud, boolean isChargeback, long amountCents) {
        // Intentionally empty.
    }

    private static long asLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
