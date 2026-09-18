package com.posgateway.aml.entity.limits;

import com.posgateway.aml.entity.merchant.Merchant;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Merchant Transaction Limits Entity
 * Stores transaction limits configured for individual merchants
 */
@Entity
@Table(name = "merchant_transaction_limits", 
       uniqueConstraints = @UniqueConstraint(columnNames = "merchant_id"))
@lombok.Getter
@lombok.Setter
// W49-P3 fix: was @EqualsAndHashCode(exclude = {"merchant"}), which still hashed on every OTHER
// field including the mutable limit amounts and timestamps -- a JPA entity's hashCode changing
// after it's already been placed in a HashSet/HashMap breaks that collection's invariants (the
// entity becomes unfindable at its own key), and two different persisted rows with coincidentally
// equal field values become "equal" even though they're different database rows. Removed
// entirely rather than switching to an id-based override, which has its own well-known pitfall
// for transient (not-yet-persisted, id == null) entities colliding with each other. Falling back
// to identity equals/hashCode (Object's default) is the standard, safest choice for a JPA entity
// per Hibernate's own guidance, and matches every sibling entity in this codebase that already
// carries no @EqualsAndHashCode at all.
@ToString(exclude = {"merchant"})
public class MerchantTransactionLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false, unique = true)
    private Merchant merchant;

    @Column(name = "daily_limit", precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    @Column(name = "weekly_limit", precision = 19, scale = 2)
    private BigDecimal weeklyLimit;

    @Column(name = "monthly_limit", precision = 19, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(name = "per_transaction_limit", precision = 19, scale = 2)
    private BigDecimal perTransactionLimit;

    /**
     * Temporary daily limit override set by a compliance officer via
     * {@code PUT /risk/limits/merchant/{id}/temporary}. When present and not yet
     * expired it takes precedence over {@link #dailyLimit} (the tighter of the two
     * is enforced). Null once cleared or never set.
     */
    @Column(name = "temporary_daily_limit", precision = 19, scale = 2)
    private BigDecimal temporaryDailyLimit;

    /** Instant at which {@link #temporaryDailyLimit} stops applying. */
    @Column(name = "temporary_limit_expires_at")
    private LocalDateTime temporaryLimitExpiresAt;

    /** User id that set the temporary limit (audit). */
    @Column(name = "temporary_limit_set_by")
    private Long temporaryLimitSetBy;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Explicit getters/setters required for some build environments where Lombok processing is not active
    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public BigDecimal getWeeklyLimit() {
        return weeklyLimit;
    }

    public void setWeeklyLimit(BigDecimal weeklyLimit) {
        this.weeklyLimit = weeklyLimit;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public BigDecimal getPerTransactionLimit() {
        return perTransactionLimit;
    }

    public void setPerTransactionLimit(BigDecimal perTransactionLimit) {
        this.perTransactionLimit = perTransactionLimit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public BigDecimal getTemporaryDailyLimit() {
        return temporaryDailyLimit;
    }

    public void setTemporaryDailyLimit(BigDecimal temporaryDailyLimit) {
        this.temporaryDailyLimit = temporaryDailyLimit;
    }

    public LocalDateTime getTemporaryLimitExpiresAt() {
        return temporaryLimitExpiresAt;
    }

    public void setTemporaryLimitExpiresAt(LocalDateTime temporaryLimitExpiresAt) {
        this.temporaryLimitExpiresAt = temporaryLimitExpiresAt;
    }

    public Long getTemporaryLimitSetBy() {
        return temporaryLimitSetBy;
    }

    public void setTemporaryLimitSetBy(Long temporaryLimitSetBy) {
        this.temporaryLimitSetBy = temporaryLimitSetBy;
    }

    /**
     * The daily limit currently in force: the temporary override when it is set and
     * not expired (the tighter of the two if a base daily limit also exists),
     * otherwise the configured {@link #dailyLimit}.
     */
    @Transient
    public BigDecimal effectiveDailyLimit(LocalDateTime now) {
        boolean tempActive = temporaryDailyLimit != null
                && temporaryLimitExpiresAt != null
                && now.isBefore(temporaryLimitExpiresAt);
        if (!tempActive) {
            return dailyLimit;
        }
        if (dailyLimit == null) {
            return temporaryDailyLimit;
        }
        return temporaryDailyLimit.min(dailyLimit);
    }
}

