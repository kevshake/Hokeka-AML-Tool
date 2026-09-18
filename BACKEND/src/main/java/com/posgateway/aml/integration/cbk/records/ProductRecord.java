package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #10 — Products Info.
 * Wrapper key: {@code PSP_PRODUCTS_INFO}
 * Schedule: Monthly, day 1.
 */
public final class ProductRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("REPORTING_DATE")
    private String reportingDate;

    @JsonProperty("PRODUCT_OWNERSHIP_FLAG")
    private String productOwnershipFlag;

    @JsonProperty("PRODUCT_OWNERSHIP_CATEGORY")
    private String productOwnershipCategory;

    @JsonProperty("PRODUCT_PARTNER_NAME")
    private String productPartnerName;

    @JsonProperty("PRODUCT_TRANSACTION_CODE")
    private String productTransactionCode;

    @JsonProperty("GENDER")
    private String gender;

    @JsonProperty("STATUS_CODE")
    private String statusCode;

    @JsonProperty("BAND_CODE")
    private String bandCode;

    @JsonProperty("NO_OF_CUSTOMERS")
    private String noOfCustomers;

    @JsonProperty("NO_OF_TRANSACTIONS")
    private String noOfTransactions;

    @JsonProperty("VALUE_OF_TRANSACTIONS")
    private String valueOfTransactions;

    @JsonProperty("PRODUCT_NAME")
    private String productName;

    public ProductRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getProductOwnershipFlag() { return productOwnershipFlag; }
    public void setProductOwnershipFlag(String productOwnershipFlag) { this.productOwnershipFlag = productOwnershipFlag; }

    public String getProductOwnershipCategory() { return productOwnershipCategory; }
    public void setProductOwnershipCategory(String productOwnershipCategory) { this.productOwnershipCategory = productOwnershipCategory; }

    public String getProductPartnerName() { return productPartnerName; }
    public void setProductPartnerName(String productPartnerName) { this.productPartnerName = productPartnerName; }

    public String getProductTransactionCode() { return productTransactionCode; }
    public void setProductTransactionCode(String productTransactionCode) { this.productTransactionCode = productTransactionCode; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getBandCode() { return bandCode; }
    public void setBandCode(String bandCode) { this.bandCode = bandCode; }

    public String getNoOfCustomers() { return noOfCustomers; }
    public void setNoOfCustomers(String noOfCustomers) { this.noOfCustomers = noOfCustomers; }

    public String getNoOfTransactions() { return noOfTransactions; }
    public void setNoOfTransactions(String noOfTransactions) { this.noOfTransactions = noOfTransactions; }

    public String getValueOfTransactions() { return valueOfTransactions; }
    public void setValueOfTransactions(String valueOfTransactions) { this.valueOfTransactions = valueOfTransactions; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
}
