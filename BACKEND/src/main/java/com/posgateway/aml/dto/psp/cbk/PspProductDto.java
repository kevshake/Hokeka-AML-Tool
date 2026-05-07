package com.posgateway.aml.dto.psp.cbk;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PspProductDto {

    private Long id;
    private Long pspId;
    private String productName;
    private String productOwnershipFlag;
    private String productOwnershipCategory;
    private String productPartnerName;
    private String productTransactionCode;
    private String genderSegment;
    private String statusCode;
    private String bandCode;
    private Long noOfCustomers;
    private Long noOfTransactions;
    private BigDecimal valueOfTransactions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PspProductDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductOwnershipFlag() { return productOwnershipFlag; }
    public void setProductOwnershipFlag(String productOwnershipFlag) { this.productOwnershipFlag = productOwnershipFlag; }

    public String getProductOwnershipCategory() { return productOwnershipCategory; }
    public void setProductOwnershipCategory(String productOwnershipCategory) { this.productOwnershipCategory = productOwnershipCategory; }

    public String getProductPartnerName() { return productPartnerName; }
    public void setProductPartnerName(String productPartnerName) { this.productPartnerName = productPartnerName; }

    public String getProductTransactionCode() { return productTransactionCode; }
    public void setProductTransactionCode(String productTransactionCode) { this.productTransactionCode = productTransactionCode; }

    public String getGenderSegment() { return genderSegment; }
    public void setGenderSegment(String genderSegment) { this.genderSegment = genderSegment; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getBandCode() { return bandCode; }
    public void setBandCode(String bandCode) { this.bandCode = bandCode; }

    public Long getNoOfCustomers() { return noOfCustomers; }
    public void setNoOfCustomers(Long noOfCustomers) { this.noOfCustomers = noOfCustomers; }

    public Long getNoOfTransactions() { return noOfTransactions; }
    public void setNoOfTransactions(Long noOfTransactions) { this.noOfTransactions = noOfTransactions; }

    public BigDecimal getValueOfTransactions() { return valueOfTransactions; }
    public void setValueOfTransactions(BigDecimal valueOfTransactions) { this.valueOfTransactions = valueOfTransactions; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
