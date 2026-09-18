package com.posgateway.aml.entity.psp;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Invoice Line Item Entity
 * Detailed breakdown of invoice charges
 */
@Entity
@Table(name = "invoice_line_items")
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_item_id")
    private Long lineItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // Line Item Details
    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "service_type", nullable = false, length = 100)
    private String serviceType;

    // Quantity & Pricing
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    // Period
    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    // Metadata
    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public InvoiceLineItem() {
    }

    public InvoiceLineItem(Long lineItemId, Invoice invoice, Integer lineNumber, String description, String serviceType,
            Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal, LocalDate periodStart, LocalDate periodEnd,
            String notes, LocalDateTime createdAt) {
        this.lineItemId = lineItemId;
        this.invoice = invoice;
        this.lineNumber = lineNumber;
        this.description = description;
        this.serviceType = serviceType;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.notes = notes;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public Long getLineItemId() {
        return lineItemId;
    }

    public void setLineItemId(Long lineItemId) {
        this.lineItemId = lineItemId;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
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

    public static InvoiceLineItemBuilder builder() {
        return new InvoiceLineItemBuilder();
    }

    public static class InvoiceLineItemBuilder {
        private Long lineItemId;
        private Invoice invoice;
        private Integer lineNumber;
        private String description;
        private String serviceType;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String notes;
        private LocalDateTime createdAt = LocalDateTime.now();

        InvoiceLineItemBuilder() {
        }

        public InvoiceLineItemBuilder lineItemId(Long lineItemId) {
            this.lineItemId = lineItemId;
            return this;
        }

        public InvoiceLineItemBuilder invoice(Invoice invoice) {
            this.invoice = invoice;
            return this;
        }

        public InvoiceLineItemBuilder lineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public InvoiceLineItemBuilder description(String description) {
            this.description = description;
            return this;
        }

        public InvoiceLineItemBuilder serviceType(String serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        public InvoiceLineItemBuilder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        public InvoiceLineItemBuilder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public InvoiceLineItemBuilder lineTotal(BigDecimal lineTotal) {
            this.lineTotal = lineTotal;
            return this;
        }

        public InvoiceLineItemBuilder periodStart(LocalDate periodStart) {
            this.periodStart = periodStart;
            return this;
        }

        public InvoiceLineItemBuilder periodEnd(LocalDate periodEnd) {
            this.periodEnd = periodEnd;
            return this;
        }

        public InvoiceLineItemBuilder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public InvoiceLineItemBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public InvoiceLineItem build() {
            return new InvoiceLineItem(lineItemId, invoice, lineNumber, description, serviceType, quantity, unitPrice,
                    lineTotal, periodStart, periodEnd, notes, createdAt);
        }

        public String toString() {
            return "InvoiceLineItem.InvoiceLineItemBuilder(lineItemId=" + this.lineItemId + ", invoice=" + this.invoice
                    + ", lineNumber=" + this.lineNumber + ", description=" + this.description + ", serviceType="
                    + this.serviceType + ", quantity=" + this.quantity + ", unitPrice=" + this.unitPrice
                    + ", lineTotal=" + this.lineTotal + ", periodStart=" + this.periodStart + ", periodEnd="
                    + this.periodEnd + ", notes=" + this.notes + ", createdAt=" + this.createdAt + ")";
        }
    }
}
