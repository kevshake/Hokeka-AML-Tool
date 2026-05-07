package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CBK GDI #11 – Trust Account balances (daily).
 * Maps to table psp_trust_accounts.
 */
@Entity
@Table(name = "psp_trust_accounts", indexes = {
        @Index(name = "idx_psp_trust_accounts_psp_id", columnList = "psp_id")
})
public class PspTrustAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "bank_id", length = 64)
    private String bankId;

    @Column(name = "bank_account_number", length = 128)
    private String bankAccountNumber;

    @Column(name = "trust_acc_dr_type_code", length = 32)
    private String trustAccDrTypeCode;

    @Column(name = "org_receiving_donation", length = 256)
    private String orgReceivingDonation;

    @Column(name = "sector_code", length = 32)
    private String sectorCode;

    @Column(name = "trust_acc_int_utilized_details", columnDefinition = "TEXT")
    private String trustAccIntUtilizedDetails;

    @Column(name = "opening_balance", precision = 18, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "principal_amount", precision = 18, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "interest_earned", precision = 18, scale = 4)
    private BigDecimal interestEarned;

    @Column(name = "closing_balance", precision = 18, scale = 4)
    private BigDecimal closingBalance;

    @Column(name = "interest_utilized", precision = 18, scale = 4)
    private BigDecimal interestUtilized;

    @Column(name = "trust_fields", columnDefinition = "TEXT")
    private String trustFields;

    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspTrustAccount() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

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

    public static PspTrustAccountBuilder builder() { return new PspTrustAccountBuilder(); }

    public static class PspTrustAccountBuilder {
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

        PspTrustAccountBuilder() {}

        public PspTrustAccountBuilder id(Long id) { this.id = id; return this; }
        public PspTrustAccountBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspTrustAccountBuilder bankId(String bankId) { this.bankId = bankId; return this; }
        public PspTrustAccountBuilder bankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; return this; }
        public PspTrustAccountBuilder trustAccDrTypeCode(String trustAccDrTypeCode) { this.trustAccDrTypeCode = trustAccDrTypeCode; return this; }
        public PspTrustAccountBuilder orgReceivingDonation(String orgReceivingDonation) { this.orgReceivingDonation = orgReceivingDonation; return this; }
        public PspTrustAccountBuilder sectorCode(String sectorCode) { this.sectorCode = sectorCode; return this; }
        public PspTrustAccountBuilder trustAccIntUtilizedDetails(String trustAccIntUtilizedDetails) { this.trustAccIntUtilizedDetails = trustAccIntUtilizedDetails; return this; }
        public PspTrustAccountBuilder openingBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; return this; }
        public PspTrustAccountBuilder principalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; return this; }
        public PspTrustAccountBuilder interestEarned(BigDecimal interestEarned) { this.interestEarned = interestEarned; return this; }
        public PspTrustAccountBuilder closingBalance(BigDecimal closingBalance) { this.closingBalance = closingBalance; return this; }
        public PspTrustAccountBuilder interestUtilized(BigDecimal interestUtilized) { this.interestUtilized = interestUtilized; return this; }
        public PspTrustAccountBuilder trustFields(String trustFields) { this.trustFields = trustFields; return this; }
        public PspTrustAccountBuilder asOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; return this; }
        public PspTrustAccountBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspTrustAccountBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspTrustAccount build() {
            PspTrustAccount e = new PspTrustAccount();
            e.id = this.id;
            e.pspId = this.pspId;
            e.bankId = this.bankId;
            e.bankAccountNumber = this.bankAccountNumber;
            e.trustAccDrTypeCode = this.trustAccDrTypeCode;
            e.orgReceivingDonation = this.orgReceivingDonation;
            e.sectorCode = this.sectorCode;
            e.trustAccIntUtilizedDetails = this.trustAccIntUtilizedDetails;
            e.openingBalance = this.openingBalance;
            e.principalAmount = this.principalAmount;
            e.interestEarned = this.interestEarned;
            e.closingBalance = this.closingBalance;
            e.interestUtilized = this.interestUtilized;
            e.trustFields = this.trustFields;
            e.asOfDate = this.asOfDate;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
