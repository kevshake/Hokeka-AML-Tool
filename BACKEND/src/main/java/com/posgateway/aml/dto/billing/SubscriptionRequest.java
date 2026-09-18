package com.posgateway.aml.dto.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for creating or updating a PSP subscription.
 */
public class SubscriptionRequest {

    private Long pspId;
    private String tierCode;
    private String billingCycle;
    private String billingCurrency;
    private BigDecimal discountPercentage;
    private LocalDate contractStart;
    private LocalDate contractEnd;
    private String notes;
    private LocalDate trialEndsAt;

    public SubscriptionRequest() {
    }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getTierCode() { return tierCode; }
    public void setTierCode(String tierCode) { this.tierCode = tierCode; }

    public String getBillingCycle() { return billingCycle; }
    public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }

    public String getBillingCurrency() { return billingCurrency; }
    public void setBillingCurrency(String billingCurrency) { this.billingCurrency = billingCurrency; }

    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(BigDecimal discountPercentage) { this.discountPercentage = discountPercentage; }

    public LocalDate getContractStart() { return contractStart; }
    public void setContractStart(LocalDate contractStart) { this.contractStart = contractStart; }

    public LocalDate getContractEnd() { return contractEnd; }
    public void setContractEnd(LocalDate contractEnd) { this.contractEnd = contractEnd; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDate getTrialEndsAt() { return trialEndsAt; }
    public void setTrialEndsAt(LocalDate trialEndsAt) { this.trialEndsAt = trialEndsAt; }
}
