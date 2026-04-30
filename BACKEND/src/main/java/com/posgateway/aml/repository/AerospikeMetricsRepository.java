package com.posgateway.aml.repository;

import com.posgateway.aml.model.MerchantMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Stub kept after Aerospike removal.
 *
 * <p>The original implementation aggregated 30 days of merchant metrics from
 * Aerospike. Aerospike is no longer reachable from this process; we return empty
 * {@link MerchantMetrics} so callers continue to compile and degrade gracefully.
 *
 * <p>TODO(aerospike-removal): wire this up to read from PostgreSQL or via the
 * AML microservice once that endpoint exists. For now consumers simply see
 * "no metrics available" which the existing scheme-monitoring code handles.
 */
@Repository
public class AerospikeMetricsRepository {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeMetricsRepository.class);

    public MerchantMetrics load30DayMetrics(String merchantId) {
        logger.debug("AerospikeMetricsRepository is a stub — returning empty metrics for {}", merchantId);
        return new MerchantMetrics();
    }

    /**
     * Stub: was an Aerospike counter increment per response code. Now a no-op.
     * TODO(aerospike-removal): forward these increments to a Micrometer counter
     * or an aml-microservice ingest endpoint.
     */
    public void incrementCounters(String merchantId, boolean isFraud, boolean isChargeback, long amountCents) {
        logger.debug("incrementCounters stub — merchant={} fraud={} chargeback={} amt={}",
                merchantId, isFraud, isChargeback, amountCents);
    }
}
