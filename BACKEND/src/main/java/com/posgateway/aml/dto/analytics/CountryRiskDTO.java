package com.posgateway.aml.dto.analytics;

/**
 * Per-country aggregate used by the dashboard risk-heatmap endpoint.
 * countryCode is the ISO-3 alpha code stored on TransactionEntity.merchantCountry.
 */
public class CountryRiskDTO {
    private String countryCode;
    private String countryName;
    private String riskLevel; // LOW | MEDIUM | HIGH
    private long transactionCount;
    private long alertCount;

    public CountryRiskDTO() {}

    public CountryRiskDTO(String countryCode, String countryName, String riskLevel,
                          long transactionCount, long alertCount) {
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.riskLevel = riskLevel;
        this.transactionCount = transactionCount;
        this.alertCount = alertCount;
    }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public long getTransactionCount() { return transactionCount; }
    public void setTransactionCount(long transactionCount) { this.transactionCount = transactionCount; }
    public long getAlertCount() { return alertCount; }
    public void setAlertCount(long alertCount) { this.alertCount = alertCount; }
}
