package com.posgateway.aml.dto.billing;

import com.posgateway.aml.entity.billing.Subscription;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for Subscription entities.
 * Maps directly from the Subscription entity without MapStruct.
 */
public class SubscriptionResponse {

    private Long subscriptionId;
    private Long pspId;
    private String pspCode;
    private String tierCode;
    private String tierName;
    private BigDecimal monthlyFeeUsd;
    private String billingCycle;
    private String billingCurrency;
    private BigDecimal discountPercentage;
    private LocalDate contractStart;
    private LocalDate contractEnd;
    private String status;
    private LocalDate trialEndsAt;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SubscriptionResponse() {
    }

    /**
     * Convenience factory — maps a Subscription entity to this DTO.
     * Safe to call even if pricingTier or psp is lazily-loaded (they are EAGER on Subscription).
     */
    public static SubscriptionResponse from(Subscription s) {
        SubscriptionResponse r = new SubscriptionResponse();
        r.subscriptionId = s.getSubscriptionId();
        if (s.getPsp() != null) {
            r.pspId = s.getPsp().getPspId();
            r.pspCode = s.getPsp().getPspCode();
        }
        if (s.getPricingTier() != null) {
            r.tierCode = s.getPricingTier().getTierCode();
            r.tierName = s.getPricingTier().getTierName();
            r.monthlyFeeUsd = s.getPricingTier().getMonthlyFeeUsd();
        }
        r.billingCycle = s.getBillingCycle();
        r.billingCurrency = s.getBillingCurrency();
        r.discountPercentage = s.getDiscountPercentage();
        r.contractStart = s.getContractStart();
        r.contractEnd = s.getContractEnd();
        r.status = s.getStatus();
        r.trialEndsAt = s.getTrialEndsAt();
        r.notes = s.getNotes();
        r.createdAt = s.getCreatedAt();
        r.updatedAt = s.getUpdatedAt();
        return r;
    }

    // Getters and setters

    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getPspCode() { return pspCode; }
    public void setPspCode(String pspCode) { this.pspCode = pspCode; }

    public String getTierCode() { return tierCode; }
    public void setTierCode(String tierCode) { this.tierCode = tierCode; }

    public String getTierName() { return tierName; }
    public void setTierName(String tierName) { this.tierName = tierName; }

    public BigDecimal getMonthlyFeeUsd() { return monthlyFeeUsd; }
    public void setMonthlyFeeUsd(BigDecimal monthlyFeeUsd) { this.monthlyFeeUsd = monthlyFeeUsd; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getTrialEndsAt() { return trialEndsAt; }
    public void setTrialEndsAt(LocalDate trialEndsAt) { this.trialEndsAt = trialEndsAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
