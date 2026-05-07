package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #11 — Trust Account.
 * Wrapper key: {@code TRUSTACCOUNT_DATA}
 * Schedule: Daily.
 */
public final class TrustAccountRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("REPORTING_DATE")
    private String reportingDate;

    @JsonProperty("BANK_ID")
    private String bankId;

    @JsonProperty("BANK_ACCOUNT_NUMBER")
    private String bankAccountNumber;

    @JsonProperty("TRUST_ACC_DR_TYPE_CODE")
    private String trustAccDrTypeCode;

    @JsonProperty("ORG_RECEIVING_DONATION")
    private String orgReceivingDonation;

    @JsonProperty("SECTOR_CODE")
    private String sectorCode;

    @JsonProperty("TRUST_ACC_INT_UTILIZED_DETAILS")
    private String trustAccIntUtilizedDetails;

    @JsonProperty("TRUST_ACC_OPENING_BALANCE")
    private String trustAccOpeningBalance;

    @JsonProperty("PRINCIPAL_AMOUNT")
    private String principalAmount;

    @JsonProperty("TRUST_ACC_INTEREST_EARNED")
    private String trustAccInterestEarned;

    @JsonProperty("CLOSING_BALANCE")
    private String closingBalance;

    @JsonProperty("TRUST_ACC_INTEREST_UTILIZED")
    private String trustAccInterestUtilized;

    @JsonProperty("TRUST_FIELDS")
    private String trustFields;

    public TrustAccountRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getBankId() { return bankId; }
    public void setBankId(String bankId) { this.bankId = bankId; }

    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }

    public String getTrustAccDrTypeCode() { return trustAccDrTypeCode; }
    public void setTrustAccDrTypeCode(String trustAccDrTypeCode) { this.trustAccDrTypeCode = trustAccDrTypeCode; }

    public String getOrgReceivingDonation() { return orgReceivingDonation; }
    public void setOrgReceivingDonation(String orgReceivingDonation) { this.orgReceivingDonation = orgReceivingDonation; }

    public String getSectorCode() { return sectorCode; }
    public void setSectorCode(String sectorCode) { this.sectorCode = sectorCode; }

    public String getTrustAccIntUtilizedDetails() { return trustAccIntUtilizedDetails; }
    public void setTrustAccIntUtilizedDetails(String trustAccIntUtilizedDetails) { this.trustAccIntUtilizedDetails = trustAccIntUtilizedDetails; }

    public String getTrustAccOpeningBalance() { return trustAccOpeningBalance; }
    public void setTrustAccOpeningBalance(String trustAccOpeningBalance) { this.trustAccOpeningBalance = trustAccOpeningBalance; }

    public String getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(String principalAmount) { this.principalAmount = principalAmount; }

    public String getTrustAccInterestEarned() { return trustAccInterestEarned; }
    public void setTrustAccInterestEarned(String trustAccInterestEarned) { this.trustAccInterestEarned = trustAccInterestEarned; }

    public String getClosingBalance() { return closingBalance; }
    public void setClosingBalance(String closingBalance) { this.closingBalance = closingBalance; }

    public String getTrustAccInterestUtilized() { return trustAccInterestUtilized; }
    public void setTrustAccInterestUtilized(String trustAccInterestUtilized) { this.trustAccInterestUtilized = trustAccInterestUtilized; }

    public String getTrustFields() { return trustFields; }
    public void setTrustFields(String trustFields) { this.trustFields = trustFields; }
}
