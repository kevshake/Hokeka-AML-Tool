package com.hokeka.txn.cache;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import com.hokeka.txn.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TxnHotCache {

  private static final Logger log = LoggerFactory.getLogger(TxnHotCache.class);
  private static final String SET_TXN_HOT = "txn_hot";

  private final AerospikeClient client;
  private final String namespace;
  private final int ttlSeconds;

  public TxnHotCache(AerospikeClient client,
                     @Value("${aml.aerospike.namespace:aml}") String namespace,
                     @Value("${aml.aerospike.txn-hot.ttl-seconds:3600}") int ttlSeconds) {
    this.client = client;
    this.namespace = namespace;
    this.ttlSeconds = ttlSeconds;
  }

  public void upsert(Transaction t) {
    try {
      Key key = new Key(namespace, SET_TXN_HOT, t.getPspId() + ":" + t.getTxnId());
      WritePolicy wp = new WritePolicy(client.writePolicyDefault);
      wp.expiration = ttlSeconds;
      client.put(wp, key,
          new Bin("txn_id", t.getTxnId()),
          new Bin("psp_id", t.getPspId()),
          new Bin("pan_hash", t.getPanHash()),
          new Bin("merchant_id", t.getMerchantId()),
          new Bin("amount_c", t.getAmountCents()),
          new Bin("currency", t.getCurrency()),
          new Bin("risk", t.getRiskLevel()),
          new Bin("decision", t.getDecision()),
          new Bin("txn_ts", t.getTxnTs() == null ? 0L : t.getTxnTs().toEpochMilli()));
    } catch (AerospikeException e) {
      log.warn("Aerospike upsert failed for {}: {}", t.getTxnId(), e.getMessage());
    }
  }

  public Map<String, Object> get(String pspId, String txnId) {
    try {
      Key key = new Key(namespace, SET_TXN_HOT, pspId + ":" + txnId);
      Record r = client.get(null, key);
      if (r == null) return null;
      return new HashMap<>(r.bins);
    } catch (AerospikeException e) {
      log.warn("Aerospike read failed for {}:{} — {}", pspId, txnId, e.getMessage());
      return null;
    }
  }
}
