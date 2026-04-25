package com.hokeka.aml.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AmlResult {
    @JsonProperty("transactionId")
    private String transactionId;
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

    public AmlResult(String transactionId, double riskScore, String decision,
                     String riskLevel, String source, long processingTimeMs) {
        this.transactionId = transactionId;
        this.riskScore = riskScore;
        this.decision = decision;
        this.riskLevel = riskLevel;
        this.source = source;
        this.processingTimeMs = processingTimeMs;
    }

    public String getTransactionId() { return transactionId; }
    public double getRiskScore() { return riskScore; }
    public String getDecision() { return decision; }
    public String getRiskLevel() { return riskLevel; }
    public String getSource() { return source; }
    public long getProcessingTimeMs() { return processingTimeMs; }
}
