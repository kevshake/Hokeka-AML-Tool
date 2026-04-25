package com.posgateway.aml.repository;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.IndexType;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
import com.aerospike.client.task.IndexTask;
import com.posgateway.aml.model.AerospikeTransaction;
import com.posgateway.aml.service.AerospikeConnectionService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class AerospikeTransactionRepository {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeTransactionRepository.class);
    private static final String SET_NAME = "transactions";

    private final AerospikeConnectionService aerospikeConnectionService;
    private final String namespace;

    @Autowired
    public AerospikeTransactionRepository(
            AerospikeConnectionService aerospikeConnectionService,
            @Value("${aerospike.namespace:test}") String namespace) {
        this.aerospikeConnectionService = aerospikeConnectionService;
        this.namespace = namespace;
    }

    @PostConstruct
    public void initIndexes() {
        if (!aerospikeConnectionService.isEnabled()) return;
        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            createIndex(client, "merchantDateKey",  "idx_txn_merchant_date");
            createIndex(client, "cardDateKey",       "idx_txn_card_date");
            createIndex(client, "statusCountryKey",  "idx_txn_status_country");
            createIndex(client, "merchantStatusKey", "idx_txn_merchant_status");
            logger.info("Aerospike transaction secondary indexes checked/created.");
        } catch (Exception e) {
            logger.error("Failed to initialize Aerospike transaction indexes: {}", e.getMessage());
        }
    }

    private void createIndex(AerospikeClient client, String binName, String indexName) {
        try {
            IndexTask task = client.createIndex(null, namespace, SET_NAME, indexName, binName, IndexType.STRING);
            task.waitTillComplete();
        } catch (com.aerospike.client.AerospikeException e) {
            if (e.getResultCode() != 202) { // 202 = index already exists
                logger.warn("Index creation warning for {}: {}", indexName, e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void save(AerospikeTransaction txn) {
        if (!aerospikeConnectionService.isEnabled()) return;
        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            WritePolicy policy = new WritePolicy();
            policy.sendKey = true;
            policy.expiration = 2592000; // 30-day TTL

            Key key = new Key(namespace, SET_NAME, txn.getTxnId());
            Bin[] bins = {
                new Bin("txnId",            txn.getTxnId()),
                new Bin("amount",           txn.getAmount() != null ? txn.getAmount().doubleValue() : 0.0),
                new Bin("currency",         txn.getCurrency()),
                new Bin("merchantId",       txn.getMerchantId()),
                new Bin("accountNumber",    txn.getAccountNumber()),
                new Bin("status",           txn.getStatus()),
                new Bin("country",          txn.getCountryCode()),
                new Bin("timestamp",        txn.getTimestamp()),
                new Bin("merchantDateKey",  txn.getMerchantDateKey()),
                new Bin("cardDateKey",      txn.getCardDateKey()),
                new Bin("statusCountryKey", txn.getStatusCountryKey()),
                new Bin("merchantStatusKey",txn.getMerchantStatusKey())
            };
            client.put(policy, key, bins);
        } catch (Exception e) {
            logger.error("Failed to save transaction {} to Aerospike: {}", txn.getTxnId(), e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Point read — sub-millisecond direct key lookup
    // -------------------------------------------------------------------------

    public AerospikeTransaction findById(String txnId) {
        if (!aerospikeConnectionService.isEnabled()) return null;
        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            Key key = new Key(namespace, SET_NAME, txnId);
            Record record = client.get(null, key);
            if (record == null) return null;
            return AerospikeTransaction.fromRecord(record, txnId);
        } catch (Exception e) {
            logger.error("Failed to lookup transaction {} from Aerospike: {}", txnId, e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Secondary-index queries — no full scans
    // -------------------------------------------------------------------------

    public List<AerospikeTransaction> findByMerchantAndDate(String merchantId, String dateStr) {
        return queryByIndex("merchantDateKey", merchantId + "#" + dateStr);
    }

    public List<AerospikeTransaction> findByCardAndDate(String panHash, String dateStr) {
        return queryByIndex("cardDateKey", panHash + "#" + dateStr);
    }

    public List<AerospikeTransaction> findByStatusAndCountry(String status, String countryCode) {
        return queryByIndex("statusCountryKey", status + "#" + countryCode);
    }

    public List<AerospikeTransaction> findByMerchantAndStatus(String merchantId, String status) {
        return queryByIndex("merchantStatusKey", merchantId + "#" + status);
    }

    private List<AerospikeTransaction> queryByIndex(String binName, String filterValue) {
        List<AerospikeTransaction> results = new ArrayList<>();
        if (!aerospikeConnectionService.isEnabled()) return results;
        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            Statement stmt = new Statement();
            stmt.setNamespace(namespace);
            stmt.setSetName(SET_NAME);
            stmt.setFilter(Filter.equal(binName, filterValue));

            try (RecordSet rs = client.query(null, stmt)) {
                while (rs.next()) {
                    Key k = rs.getKey();
                    Record record = rs.getRecord();
                    String txnId = record.getString("txnId");
                    if (txnId == null && k != null && k.userKey != null) {
                        txnId = k.userKey.toString();
                    }
                    results.add(AerospikeTransaction.fromRecord(record, txnId));
                }
            }
        } catch (Exception e) {
            logger.error("Error querying Aerospike index {} = {}: {}", binName, filterValue, e.getMessage());
        }
        return results;
    }
}
