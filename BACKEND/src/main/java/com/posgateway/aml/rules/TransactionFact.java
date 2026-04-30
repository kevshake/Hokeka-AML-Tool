package com.posgateway.aml.rules;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaction Fact for Drools Rules Engine.
 * This object is inserted into the Drools session for rule evaluation.
 * Mutable fields allow rules to set decisions and reasons.
 */
public class TransactionFact {

    // Immutable transaction data
    private final Long txnId;
    private final String merchantId;
    private final BigDecimal amount;
    private final String currency;
    private final String countryCode;
    private final LocalDateTime txnTime;
    private final String channel;
    private final String panHash;

    // ML/Graph scores (from XGBoost and Neo4j GDS)
    private final Double mlScore;
    private final Double pageRank;
    private final Long communityId;
    private final Double betweenness;
    private final Long connectionCount;

    // Velocity features
    private final Long panTxnCount1h;
    private final Double panAmountSum24h;
    private final Double merchantAmountSum24h;

    // Flagright Risk Scores
    private final Double krs;
    private final Double cra;
    private final Double trs;

    // Mutable decision fields (set by rules)
    private String decision = "ALLOW";
    private final List<String> reasons = new ArrayList<>();
    private final List<String> triggeredRules = new ArrayList<>();
    private boolean sarRequired = false;
    private boolean ctrRequired = false;

    public TransactionFact(Long txnId, String merchantId, BigDecimal amount, String currency,
            String countryCode, LocalDateTime txnTime, String channel, String panHash,
            Double mlScore, Double pageRank, Long communityId, Double betweenness,
            Long connectionCount, Long panTxnCount1h, Double panAmountSum24h,
            Double merchantAmountSum24h, Double krs, Double cra, Double trs) {
        this.txnId = txnId;
        this.merchantId = merchantId;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.currency = currency != null ? currency : "USD";
        this.countryCode = countryCode != null ? countryCode : "UNK";
        this.txnTime = txnTime != null ? txnTime : LocalDateTime.now();
        this.channel = channel;
        this.panHash = panHash;
        this.mlScore = mlScore != null ? mlScore : 0.0;
        this.pageRank = pageRank != null ? pageRank : 0.0;
        this.communityId = communityId != null ? communityId : 0L;
        this.betweenness = betweenness != null ? betweenness : 0.0;
        this.connectionCount = connectionCount != null ? connectionCount : 0L;
        this.panTxnCount1h = panTxnCount1h != null ? panTxnCount1h : 0L;
        this.panAmountSum24h = panAmountSum24h != null ? panAmountSum24h : 0.0;
        this.merchantAmountSum24h = merchantAmountSum24h != null ? merchantAmountSum24h : 0.0;
        this.krs = krs != null ? krs : 0.0;
        this.cra = cra != null ? cra : 0.0;
        this.trs = trs != null ? trs : 0.0;
    }

    // Getters for immutable fields
    public Long getTxnId() {
        return txnId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public LocalDateTime getTxnTime() {
        return txnTime;
    }

    public String getChannel() {
        return channel;
    }

    public String getPanHash() {
        return panHash;
    }

    public Double getMlScore() {
        return mlScore;
    }

    public Double getPageRank() {
        return pageRank;
    }

    public Long getCommunityId() {
        return communityId;
    }

    public Double getBetweenness() {
        return betweenness;
    }

    public Long getConnectionCount() {
        return connectionCount;
    }

    public Long getPanTxnCount1h() {
        return panTxnCount1h;
    }

    public Double getPanAmountSum24h() {
        return panAmountSum24h;
    }

    public Double getMerchantAmountSum24h() {
        return merchantAmountSum24h;
    }

    public Double getKrs() {
        return krs;
    }

    public Double getCra() {
        return cra;
    }

    public Double getTrs() {
        return trs;
    }

    // Decision methods (used by rules)
    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void addReason(String reason) {
        this.reasons.add(reason);
    }

    public List<String> getTriggeredRules() {
        return triggeredRules;
    }

    public void addTriggeredRule(String rule) {
        this.triggeredRules.add(rule);
    }

    public boolean isSarRequired() {
        return sarRequired;
    }

    public void setSarRequired(boolean sarRequired) {
        this.sarRequired = sarRequired;
    }

    public boolean isCtrRequired() {
        return ctrRequired;
    }

    public void setCtrRequired(boolean ctrRequired) {
        this.ctrRequired = ctrRequired;
    }

    // Helper methods for rules
    public double getAmountAsDouble() {
        return amount.doubleValue();
    }

    public boolean isHighRiskCountry() {
        // OFAC sanctioned and high-risk countries
        return List.of("KP", "IR", "SY", "CU", "RU", "BY").contains(countryCode);
    }

    public boolean isHighValueTransaction() {
        return amount.compareTo(new BigDecimal("10000")) >= 0;
    }
}
