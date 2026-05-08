package com.posgateway.aml.repository;

import com.posgateway.aml.model.MerchantMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Compatibility shim kept after Aerospike removal — metrics are now reported
 * via Micrometer / the AML microservice and no longer aggregated in-process.
 *
 * <p>The original implementation aggregated 30 days of merchant metrics from
 * Aerospike. Aerospike is no longer reachable from this process; we return
 * empty {@link MerchantMetrics} so callers (scheme-monitoring report,
 * VFMP/HECM simulators) continue to compile and degrade gracefully.
 */
@Repository
public class AerospikeMetricsRepository {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeMetricsRepository.class);

    /** Returns an empty {@link MerchantMetrics} — callers handle "no data". */
    public MerchantMetrics load30DayMetrics(String merchantId) {
        logger.debug("AerospikeMetricsRepository shim — returning empty metrics for {}", merchantId);
        return new MerchantMetrics();
    }

    /** Counter increments are now Micrometer-backed elsewhere; this is a no-op. */
    public void incrementCounters(String merchantId, boolean isFraud, boolean isChargeback, long amountCents) {
        logger.debug("incrementCounters shim — merchant={} fraud={} chargeback={} amt={}",
                merchantId, isFraud, isChargeback, amountCents);
    }
}
