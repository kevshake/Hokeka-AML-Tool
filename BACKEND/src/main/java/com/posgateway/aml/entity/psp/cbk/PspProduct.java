package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CBK GDI #10 – Products Info (monthly, day 1).
 * Maps to table psp_products.
 */
@Entity
@Table(name = "psp_products", indexes = {
        @Index(name = "idx_psp_products_psp_id", columnList = "psp_id")
})
public class PspProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "product_name", length = 256)
    private String productName;

    @Column(name = "product_ownership_flag", length = 16)
    private String productOwnershipFlag;

    @Column(name = "product_ownership_category", length = 64)
    private String productOwnershipCategory;

    @Column(name = "product_partner_name", length = 256)
    private String productPartnerName;

    @Column(name = "product_transaction_code", length = 64)
    private String productTransactionCode;

    @Column(name = "gender_segment", length = 16)
    private String genderSegment;

    @Column(name = "status_code", length = 32)
    private String statusCode;

    @Column(name = "band_code", length = 32)
    private String bandCode;

    @Column(name = "no_of_customers")
    private Long noOfCustomers;

    @Column(name = "no_of_transactions")
    private Long noOfTransactions;

    @Column(name = "value_of_transactions", precision = 18, scale = 4)
    private BigDecimal valueOfTransactions;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspProduct() {}

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

    public static PspProductBuilder builder() { return new PspProductBuilder(); }

    public static class PspProductBuilder {
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

        PspProductBuilder() {}

        public PspProductBuilder id(Long id) { this.id = id; return this; }
        public PspProductBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspProductBuilder productName(String productName) { this.productName = productName; return this; }
        public PspProductBuilder productOwnershipFlag(String productOwnershipFlag) { this.productOwnershipFlag = productOwnershipFlag; return this; }
        public PspProductBuilder productOwnershipCategory(String productOwnershipCategory) { this.productOwnershipCategory = productOwnershipCategory; return this; }
        public PspProductBuilder productPartnerName(String productPartnerName) { this.productPartnerName = productPartnerName; return this; }
        public PspProductBuilder productTransactionCode(String productTransactionCode) { this.productTransactionCode = productTransactionCode; return this; }
        public PspProductBuilder genderSegment(String genderSegment) { this.genderSegment = genderSegment; return this; }
        public PspProductBuilder statusCode(String statusCode) { this.statusCode = statusCode; return this; }
        public PspProductBuilder bandCode(String bandCode) { this.bandCode = bandCode; return this; }
        public PspProductBuilder noOfCustomers(Long noOfCustomers) { this.noOfCustomers = noOfCustomers; return this; }
        public PspProductBuilder noOfTransactions(Long noOfTransactions) { this.noOfTransactions = noOfTransactions; return this; }
        public PspProductBuilder valueOfTransactions(BigDecimal valueOfTransactions) { this.valueOfTransactions = valueOfTransactions; return this; }
        public PspProductBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspProductBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspProduct build() {
            PspProduct e = new PspProduct();
            e.id = this.id;
            e.pspId = this.pspId;
            e.productName = this.productName;
            e.productOwnershipFlag = this.productOwnershipFlag;
            e.productOwnershipCategory = this.productOwnershipCategory;
            e.productPartnerName = this.productPartnerName;
            e.productTransactionCode = this.productTransactionCode;
            e.genderSegment = this.genderSegment;
            e.statusCode = this.statusCode;
            e.bandCode = this.bandCode;
            e.noOfCustomers = this.noOfCustomers;
            e.noOfTransactions = this.noOfTransactions;
            e.valueOfTransactions = this.valueOfTransactions;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
