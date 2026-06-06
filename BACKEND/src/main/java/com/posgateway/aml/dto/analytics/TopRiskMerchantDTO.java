package com.posgateway.aml.dto.analytics;

/**
 * Compact merchant projection used by the dashboard top-risk-merchants endpoint.
 */
public class TopRiskMerchantDTO {
    private int rank;
    private Long merchantId;
    private String name;
    private Double riskScore;
    private String riskLevel;

    public TopRiskMerchantDTO() {}

    public TopRiskMerchantDTO(int rank, Long merchantId, String name,
                              Double riskScore, String riskLevel) {
        this.rank = rank;
        this.merchantId = merchantId;
        this.name = name;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
    }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
}
