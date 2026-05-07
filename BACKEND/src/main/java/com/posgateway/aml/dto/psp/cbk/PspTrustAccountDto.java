package com.posgateway.aml.dto.psp.cbk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PspTrustAccountDto {

    private Long id;
    private Long pspId;
    private String bankId;
    private String bankAccountNumber;
    private String trustAccDrTypeCode;
    private String orgReceivingDonation;
    private String sectorCode;
    private String trustAccIntUtilizedDetails;
    private BigDecimal openingBalance;
    private BigDecimal principalAmount;
    private BigDecimal interestEarned;
    private BigDecimal closingBalance;
    private BigDecimal interestUtilized;
    private String trustFields;
    private LocalDate asOfDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PspTrustAccountDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

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

    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }

    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }

    public BigDecimal getInterestEarned() { return interestEarned; }
    public void setInterestEarned(BigDecimal interestEarned) { this.interestEarned = interestEarned; }

    public BigDecimal getClosingBalance() { return closingBalance; }
    public void setClosingBalance(BigDecimal closingBalance) { this.closingBalance = closingBalance; }

    public BigDecimal getInterestUtilized() { return interestUtilized; }
    public void setInterestUtilized(BigDecimal interestUtilized) { this.interestUtilized = interestUtilized; }

    public String getTrustFields() { return trustFields; }
    public void setTrustFields(String trustFields) { this.trustFields = trustFields; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
