package com.hokeka.aml.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class AmlResult {
    @JsonProperty("transactionId")
    private String transactionId;
    @JsonProperty("pspId")
    private Long pspId;
    @JsonProperty("riskScore")
    private double riskScore;
    @JsonProperty("decision")
    private String decision;
    @JsonProperty("riskLevel")
    private String riskLevel;
    @JsonProperty("source")
    private String source;
    @JsonProperty("processingTimeMs")
    private long processingTimeMs;
    /** {@code L1_AEROSPIKE} when served from Aerospike, {@code COMPUTED} when freshly scored. */
    @JsonProperty("cacheLayer")
    private String cacheLayer;
    /** Free-form risk indicators (e.g. SANCTIONS_FLAGGED). Empty when none apply. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("indicators")
    private List<String> indicators = new ArrayList<>();

    public AmlResult() {}

    public AmlResult(String transactionId, Long pspId, double riskScore, String decision,
                     String riskLevel, String source, long processingTimeMs, String cacheLayer) {
        this.transactionId = transactionId;
        this.pspId = pspId;
        this.riskScore = riskScore;
        this.decision = decision;
        this.riskLevel = riskLevel;
        this.source = source;
        this.processingTimeMs = processingTimeMs;
        this.cacheLayer = cacheLayer;
    }

    public String getTransactionId() { return transactionId; }
    public Long getPspId() { return pspId; }
    public double getRiskScore() { return riskScore; }
    public String getDecision() { return decision; }
    public String getRiskLevel() { return riskLevel; }
    public String getSource() { return source; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    public String getCacheLayer() { return cacheLayer; }
    public List<String> getIndicators() { return indicators; }

    public void setTransactionId(String v) { this.transactionId = v; }
    public void setPspId(Long v) { this.pspId = v; }
    public void setRiskScore(double v) { this.riskScore = v; }
    public void setDecision(String v) { this.decision = v; }
    public void setRiskLevel(String v) { this.riskLevel = v; }
    public void setSource(String v) { this.source = v; }
    public void setProcessingTimeMs(long v) { this.processingTimeMs = v; }
    public void setCacheLayer(String v) { this.cacheLayer = v; }
    public void setIndicators(List<String> v) { this.indicators = v != null ? v : new ArrayList<>(); }
    public void addIndicator(String i) {
        if (i == null || i.isBlank()) return;
        if (this.indicators == null) this.indicators = new ArrayList<>();
        this.indicators.add(i);
    }
}
