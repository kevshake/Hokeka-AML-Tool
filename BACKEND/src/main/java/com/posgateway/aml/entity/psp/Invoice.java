package com.posgateway.aml.entity.psp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Invoice Entity
 * Monthly billing invoices for PSPs
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Long invoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psp_id", nullable = false)
    private Psp psp;

    // Invoice Details
    @Column(name = "invoice_number", unique = true, nullable = false, length = 100)
    private String invoiceNumber;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDate billingPeriodEnd;

    // Amounts
    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_reason", columnDefinition = "text")
    private String discountReason;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    // Payment Status
    @Column(name = "status", length = 50)
    private String status = "DRAFT";

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "payment_amount", precision = 12, scale = 2)
    private BigDecimal paymentAmount;

    // Notes
    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "internal_notes", columnDefinition = "text")
    private String internalNotes;

    // Timestamps
    @Column(name = "generated_at")
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Relationships
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    public Invoice() {
    }

    public Invoice(Long invoiceId, Psp psp, String invoiceNumber, LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal taxRate,
            BigDecimal discountAmount, String discountReason, BigDecimal totalAmount, String currency, String status,
            LocalDate dueDate, LocalDateTime paidAt, String paymentMethod, String paymentReference,
            BigDecimal paymentAmount, String notes, String internalNotes, LocalDateTime generatedAt,
            LocalDateTime sentAt, LocalDateTime remindedAt, LocalDateTime createdAt, LocalDateTime updatedAt,
            List<InvoiceLineItem> lineItems) {
        this.invoiceId = invoiceId;
        this.psp = psp;
        this.invoiceNumber = invoiceNumber;
        this.billingPeriodStart = billingPeriodStart;
        this.billingPeriodEnd = billingPeriodEnd;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.taxRate = taxRate;
        this.discountAmount = discountAmount;
        this.discountReason = discountReason;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = status;
        this.dueDate = dueDate;
        this.paidAt = paidAt;
        this.paymentMethod = paymentMethod;
        this.paymentReference = paymentReference;
        this.paymentAmount = paymentAmount;
        this.notes = notes;
        this.internalNotes = internalNotes;
        this.generatedAt = generatedAt;
        this.sentAt = sentAt;
        this.remindedAt = remindedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lineItems = lineItems;
    }

    public static InvoiceBuilder builder() {
        return new InvoiceBuilder();
    }

    public static class InvoiceBuilder {
        private Long invoiceId;
        private Psp psp;
        private String invoiceNumber;
        private LocalDate billingPeriodStart;
        private LocalDate billingPeriodEnd;
        private BigDecimal subtotal;
        private BigDecimal taxAmount = BigDecimal.ZERO;
        private BigDecimal taxRate = BigDecimal.ZERO;
        private BigDecimal discountAmount = BigDecimal.ZERO;
        private String discountReason;
        private BigDecimal totalAmount;
        private String currency = "USD";
        private String status = "DRAFT";
        private LocalDate dueDate;
        private LocalDateTime paidAt;
        private String paymentMethod;
        private String paymentReference;
        private BigDecimal paymentAmount;
        private String notes;
        private String internalNotes;
        private LocalDateTime generatedAt = LocalDateTime.now();
        private LocalDateTime sentAt;
        private LocalDateTime remindedAt;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();
        private List<InvoiceLineItem> lineItems = new ArrayList<>();

        InvoiceBuilder() {
        }

        public InvoiceBuilder invoiceId(Long invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public InvoiceBuilder psp(Psp psp) {
            this.psp = psp;
            return this;
        }

        public InvoiceBuilder invoiceNumber(String invoiceNumber) {
            this.invoiceNumber = invoiceNumber;
            return this;
        }

        public InvoiceBuilder billingPeriodStart(LocalDate billingPeriodStart) {
            this.billingPeriodStart = billingPeriodStart;
            return this;
        }

        public InvoiceBuilder billingPeriodEnd(LocalDate billingPeriodEnd) {
            this.billingPeriodEnd = billingPeriodEnd;
            return this;
        }

        public InvoiceBuilder subtotal(BigDecimal subtotal) {
            this.subtotal = subtotal;
            return this;
        }

        public InvoiceBuilder taxAmount(BigDecimal taxAmount) {
            this.taxAmount = taxAmount;
            return this;
        }

        public InvoiceBuilder taxRate(BigDecimal taxRate) {
            this.taxRate = taxRate;
            return this;
        }

        public InvoiceBuilder discountAmount(BigDecimal discountAmount) {
            this.discountAmount = discountAmount;
            return this;
        }

        public InvoiceBuilder discountReason(String discountReason) {
            this.discountReason = discountReason;
            return this;
        }

        public InvoiceBuilder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public InvoiceBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public InvoiceBuilder status(String status) {
            this.status = status;
            return this;
        }

        public InvoiceBuilder dueDate(LocalDate dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public InvoiceBuilder paidAt(LocalDateTime paidAt) {
            this.paidAt = paidAt;
            return this;
        }

        public InvoiceBuilder paymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public InvoiceBuilder paymentReference(String paymentReference) {
            this.paymentReference = paymentReference;
            return this;
        }

        public InvoiceBuilder paymentAmount(BigDecimal paymentAmount) {
            this.paymentAmount = paymentAmount;
            return this;
        }

        public InvoiceBuilder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public InvoiceBuilder internalNotes(String internalNotes) {
            this.internalNotes = internalNotes;
            return this;
        }

        public InvoiceBuilder generatedAt(LocalDateTime generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public InvoiceBuilder sentAt(LocalDateTime sentAt) {
            this.sentAt = sentAt;
            return this;
        }

        public InvoiceBuilder remindedAt(LocalDateTime remindedAt) {
            this.remindedAt = remindedAt;
            return this;
        }

        public InvoiceBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public InvoiceBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public InvoiceBuilder lineItems(List<InvoiceLineItem> lineItems) {
            this.lineItems = lineItems;
            return this;
        }

        public Invoice build() {
            return new Invoice(invoiceId, psp, invoiceNumber, billingPeriodStart, billingPeriodEnd, subtotal, taxAmount,
                    taxRate, discountAmount, discountReason, totalAmount, currency, status, dueDate, paidAt,
                    paymentMethod, paymentReference, paymentAmount, notes, internalNotes, generatedAt, sentAt,
                    remindedAt, createdAt, updatedAt, lineItems);
        }
    }

    // Getters and Setters

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public Psp getPsp() {
        return psp;
    }

    public void setPsp(Psp psp) {
        this.psp = psp;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getBillingPeriodStart() {
        return billingPeriodStart;
    }

    public void setBillingPeriodStart(LocalDate billingPeriodStart) {
        this.billingPeriodStart = billingPeriodStart;
    }

    public LocalDate getBillingPeriodEnd() {
        return billingPeriodEnd;
    }

    public void setBillingPeriodEnd(LocalDate billingPeriodEnd) {
        this.billingPeriodEnd = billingPeriodEnd;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getDiscountReason() {
        return discountReason;
    }

    public void setDiscountReason(String discountReason) {
        this.discountReason = discountReason;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getInternalNotes() {
        return internalNotes;
    }

    public void setInternalNotes(String internalNotes) {
        this.internalNotes = internalNotes;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getRemindedAt() {
        return remindedAt;
    }

    public void setRemindedAt(LocalDateTime remindedAt) {
        this.remindedAt = remindedAt;
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

    public List<InvoiceLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<InvoiceLineItem> lineItems) {
        this.lineItems = lineItems;
    }

    // Helper Methods
    public void addLineItem(InvoiceLineItem lineItem) {
        lineItems.add(lineItem);
        lineItem.setInvoice(this);
    }

    public boolean isPaid() {
        return "PAID".equals(status);
    }

    public boolean isOverdue() {
        return ("SENT".equals(status) || "OVERDUE".equals(status))
                && dueDate.isBefore(LocalDate.now());
    }

    public void markAsSent() {
        this.status = "SENT";
        this.sentAt = LocalDateTime.now();
    }

    public void markAsPaid(String paymentRef, BigDecimal amount) {
        this.status = "PAID";
        this.paidAt = LocalDateTime.now();
        this.paymentReference = paymentRef;
        this.paymentAmount = amount;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
