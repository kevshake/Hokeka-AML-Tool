package com.posgateway.aml.repository;

import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import com.posgateway.aml.model.MerchantMetrics;
import com.posgateway.aml.service.AerospikeConnectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Repository
public class AerospikeMetricsRepository {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeMetricsRepository.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final String SET_NAME = "merchant_daily";
    private static final int THIRTY_DAYS = 30;

    private final String namespace;
    private final AerospikeConnectionService aerospikeConnectionService;

    @Autowired
    public AerospikeMetricsRepository(
            AerospikeConnectionService aerospikeConnectionService,
            @Value("${aerospike.namespace:sanctions}") String namespace) {
        this.aerospikeConnectionService = aerospikeConnectionService;
        this.namespace = namespace;
    }

    /**
     * Load rolling 30-day metrics for a merchant
     */
    public MerchantMetrics load30DayMetrics(String merchantId) {
        long totalTx = 0;
        long fraudTx = 0;
        long chargebackCount = 0;
        long fraudAmount = 0;
        long chargebackAmount = 0;

        LocalDate today = LocalDate.now();

        // If Aerospike is disabled, fail open and return empty metrics
        if (!aerospikeConnectionService.isEnabled()) {
            logger.info("Aerospike is disabled. Returning empty metrics for merchant {}", merchantId);
            return new MerchantMetrics();
        }

        com.aerospike.client.AerospikeClient client;
        try {
            client = aerospikeConnectionService.getClient();
        } catch (IllegalStateException e) {
            logger.warn("Aerospike client not available ({}). Returning empty metrics for merchant {}",
                    e.getMessage(), merchantId);
            return new MerchantMetrics();
        }

        // Check if client is connected to avoid blocking
        if (!client.isConnected()) {
            logger.warn("Aerospike client not connected. Returning empty metrics for merchant {}", merchantId);
            return new MerchantMetrics();
        }

        try {
            // Aggregate last 30 days
            for (int i = 0; i < THIRTY_DAYS; i++) {
                String dateKey = today.minusDays(i).format(DATE_FORMATTER);
                Key key = new Key(namespace, SET_NAME, merchantId + ":" + dateKey);

                Record record = client.get(null, key);
                if (record != null) {
                    totalTx += record.getLong("total_tx");
                    fraudTx += record.getLong("fraud_tx");
                    chargebackCount += record.getLong("chargeback_count");

                    // Safe retrieval for amounts if they exist
                    if (record.bins.containsKey("fraud_amount")) {
                        fraudAmount += record.getLong("fraud_amount");
                    }
                    if (record.bins.containsKey("chargeback_amount")) {
                        chargebackAmount += record.getLong("chargeback_amount");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving metrics from Aerospike for merchant {}: {}", merchantId, e.getMessage());
            // Return empty metrics on error to fail open (don't block flow)
            return new MerchantMetrics();
        }

        return new MerchantMetrics(totalTx, fraudTx, chargebackCount, fraudAmount, chargebackAmount);
    }

    /**
     * Increment counters (called by TransactionResultController)
     */
    public void incrementCounters(String merchantId, boolean isfraud, boolean isChargeback, long amountCents) {
        // If Aerospike is disabled or unavailable, safely no-op
        if (!aerospikeConnectionService.isEnabled()) {
            return;
        }

        com.aerospike.client.AerospikeClient client;
        try {
            client = aerospikeConnectionService.getClient();
        } catch (IllegalStateException e) {
            logger.warn("Aerospike client not available when incrementing counters for merchant {}: {}",
                    merchantId, e.getMessage());
            return;
        }

        try {
            String dateKey = LocalDate.now().format(DATE_FORMATTER);
            Key key = new Key(namespace, SET_NAME, merchantId + ":" + dateKey);

            WritePolicy writePolicy = new WritePolicy();
            writePolicy.expiration = 60 * 60 * 24 * 45; // 45 days retention

            Bin totalTxBin = new Bin("total_tx", 1);

            if (isfraud) {
                Bin fraudTxBin = new Bin("fraud_tx", 1);
                Bin fraudAmountBin = new Bin("fraud_amount", amountCents);
                client.add(writePolicy, key, totalTxBin, fraudTxBin, fraudAmountBin);
            } else if (isChargeback) {
                Bin cbTxBin = new Bin("chargeback_count", 1);
                Bin cbAmountBin = new Bin("chargeback_amount", amountCents);
                // Chargeback doesn't necessarily mean new txn count in this context (it's a
                // backend event),
                // but for HECM denominator is usually 'sales transactions in previous month'.
                // Here we just incr chargeback and keep totalTx as is (assuming totalTx was
                // incremented at auth time).
                client.add(writePolicy, key, cbTxBin, cbAmountBin);
            } else {
                // Normal transaction
                client.add(writePolicy, key, totalTxBin);
            }

        } catch (Exception e) {
            logger.error("Error updating Aerospike metrics for merchant {}: {}", merchantId, e.getMessage());
        }
    }
}
