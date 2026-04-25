package com.hokeka.aml.service;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import com.hokeka.aml.model.AmlResult;
import com.hokeka.aml.model.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AmlCheckService {
    private static final Logger log = LoggerFactory.getLogger(AmlCheckService.class);
    private static final String NAMESPACE = "test";
    private static final String SET_NAME = "aml_transactions";

    @Autowired(required = false)
    private AerospikeClient aerospikeClient;

    public AmlResult check(TransactionRequest request) {
        long startTime = System.currentTimeMillis();
        String txnId = request.getTransactionId() != null ? request.getTransactionId()
                : "TXN-" + System.currentTimeMillis();

        // Try Aerospike cache first
        if (aerospikeClient != null && aerospikeClient.isConnected()) {
            try {
                Key key = new Key(NAMESPACE, SET_NAME, txnId);
                Record record = aerospikeClient.get(null, key);
                if (record != null) {
                    double cachedScore = ((Number) record.getValue("risk_score")).doubleValue();
                    String cachedDecision = (String) record.getValue("decision");
                    long elapsed = System.currentTimeMillis() - startTime;
                    return new AmlResult(txnId, cachedScore, cachedDecision,
                            getRiskLevel(cachedScore), "aerospike_cache", elapsed);
                }
            } catch (Exception e) {
                log.warn("Aerospike lookup failed for {}: {}", txnId, e.getMessage());
            }
        }

        // Compute risk score using rule-based XGBoost-approximation
        double riskScore = computeRiskScore(request);
        String decision = riskScore >= 0.7 ? "BLOCK" : riskScore >= 0.4 ? "REVIEW" : "APPROVE";

        // Store result in Aerospike for future lookups
        if (aerospikeClient != null && aerospikeClient.isConnected()) {
            try {
                Key key = new Key(NAMESPACE, SET_NAME, txnId);
                WritePolicy wp = new WritePolicy();
                wp.expiration = 3600; // TTL 1 hour
                aerospikeClient.put(wp, key,
                        new Bin("risk_score", riskScore),
                        new Bin("decision", decision),
                        new Bin("merchant_id", request.getMerchantId()),
                        new Bin("amount", request.getAmount() != null ? request.getAmount().doubleValue() : 0.0));
            } catch (Exception e) {
                log.warn("Aerospike write failed for {}: {}", txnId, e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new AmlResult(txnId, riskScore, decision, getRiskLevel(riskScore), "computed", elapsed);
    }

    private double computeRiskScore(TransactionRequest request) {
        double score = 0.1; // baseline

        if (request.getAmount() != null) {
            BigDecimal amount = request.getAmount();
            if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) score += 0.3;
            else if (amount.compareTo(BigDecimal.valueOf(5000)) > 0) score += 0.15;
            else if (amount.compareTo(BigDecimal.valueOf(1000)) > 0) score += 0.05;
        }

        String country = request.getCountry();
        if (country != null) {
            if ("IR,KP,SY,CU,SD".contains(country)) score += 0.4;
            else if ("NG,RU,CN,VE".contains(country)) score += 0.1;
        }

        String txnType = request.getTransactionType();
        if ("CRYPTO_PURCHASE".equals(txnType) || "CASH_WITHDRAWAL".equals(txnType)) score += 0.2;
        else if ("WIRE_TRANSFER".equals(txnType)) score += 0.1;

        return Math.min(score, 1.0);
    }

    private String getRiskLevel(double score) {
        if (score >= 0.7) return "HIGH";
        if (score >= 0.4) return "MEDIUM";
        return "LOW";
    }
}
