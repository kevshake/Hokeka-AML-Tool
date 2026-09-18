package com.posgateway.aml.entity.billing;

import com.posgateway.aml.entity.psp.Psp;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Subscription Entity
 * Tracks PSP/customer subscriptions to pricing tiers
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long subscriptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psp_id", nullable = false)
    private Psp psp;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tier_id", nullable = false)
    private PricingTier pricingTier;

    @Column(name = "billing_currency", length = 3)
    private String billingCurrency = "USD";

    @Column(name = "billing_cycle", length = 20)
    private String billingCycle = "MONTHLY";

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @Column(name = "contract_start", nullable = false)
    private LocalDate contractStart;

    @Column(name = "contract_end")
    private LocalDate contractEnd;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "trial_ends_at")
    private LocalDate trialEndsAt;

    @Column(name = "rollover_credits")
    private Integer rolloverCredits = 0;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Constructors
    public Subscription() {
    }

    // Business methods

    public boolean isActive() {
        return "ACTIVE".equals(status) || "TRIAL".equals(status);
    }

    public boolean isInTrial() {
        return "TRIAL".equals(status) &&
                trialEndsAt != null &&
                LocalDate.now().isBefore(trialEndsAt);
    }

    public boolean hasExpired() {
        return contractEnd != null && LocalDate.now().isAfter(contractEnd);
    }

    // Getters and Setters
    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Psp getPsp() {
        return psp;
    }

    public void setPsp(Psp psp) {
        this.psp = psp;
    }

    public PricingTier getPricingTier() {
        return pricingTier;
    }

    public void setPricingTier(PricingTier pricingTier) {
        this.pricingTier = pricingTier;
    }

    public String getBillingCurrency() {
        return billingCurrency;
    }

    public void setBillingCurrency(String billingCurrency) {
        this.billingCurrency = billingCurrency;
    }

    public String getBillingCycle() {
        return billingCycle;
    }

    public void setBillingCycle(String billingCycle) {
        this.billingCycle = billingCycle;
    }

    public BigDecimal getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public LocalDate getContractStart() {
        return contractStart;
    }

    public void setContractStart(LocalDate contractStart) {
        this.contractStart = contractStart;
    }

    public LocalDate getContractEnd() {
        return contractEnd;
    }

    public void setContractEnd(LocalDate contractEnd) {
        this.contractEnd = contractEnd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getTrialEndsAt() {
        return trialEndsAt;
    }

    public void setTrialEndsAt(LocalDate trialEndsAt) {
        this.trialEndsAt = trialEndsAt;
    }

    public Integer getRolloverCredits() {
        return rolloverCredits;
    }

    public void setRolloverCredits(Integer rolloverCredits) {
        this.rolloverCredits = rolloverCredits;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
