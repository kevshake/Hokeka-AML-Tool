package com.posgateway.aml.dto.psp;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class InvoiceResponse {
    private String invoiceNumber;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private String status;
    private List<LineItem> lineItems;

    public InvoiceResponse() {
    }

    public InvoiceResponse(String invoiceNumber, LocalDate periodStart, LocalDate periodEnd, BigDecimal amount,
            String status, List<LineItem> lineItems) {
        this.invoiceNumber = invoiceNumber;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.amount = amount;
        this.status = status;
        this.lineItems = lineItems;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<LineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<LineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public static InvoiceResponseBuilder builder() {
        return new InvoiceResponseBuilder();
    }

    public static class InvoiceResponseBuilder {
        private String invoiceNumber;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private BigDecimal amount;
        private String status;
        private List<LineItem> lineItems;

        InvoiceResponseBuilder() {
        }

        public InvoiceResponseBuilder invoiceNumber(String invoiceNumber) {
            this.invoiceNumber = invoiceNumber;
            return this;
        }

        public InvoiceResponseBuilder periodStart(LocalDate periodStart) {
            this.periodStart = periodStart;
            return this;
        }

        public InvoiceResponseBuilder periodEnd(LocalDate periodEnd) {
            this.periodEnd = periodEnd;
            return this;
        }

        public InvoiceResponseBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public InvoiceResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public InvoiceResponseBuilder lineItems(List<LineItem> lineItems) {
            this.lineItems = lineItems;
            return this;
        }

        public InvoiceResponse build() {
            return new InvoiceResponse(invoiceNumber, periodStart, periodEnd, amount, status, lineItems);
        }

        public String toString() {
            return "InvoiceResponse.InvoiceResponseBuilder(invoiceNumber=" + this.invoiceNumber + ", periodStart="
                    + this.periodStart + ", periodEnd=" + this.periodEnd + ", amount=" + this.amount + ", status="
                    + this.status + ", lineItems=" + this.lineItems + ")";
        }
    }

    public static class LineItem {
        private String serviceType;
        private String description;
        private int quantity;
        private BigDecimal total;

        public LineItem() {
        }

        public LineItem(String serviceType, String description, int quantity, BigDecimal total) {
            this.serviceType = serviceType;
            this.description = description;
            this.quantity = quantity;
            this.total = total;
        }

        public String getServiceType() {
            return serviceType;
        }

        public void setServiceType(String serviceType) {
            this.serviceType = serviceType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getTotal() {
            return total;
        }

        public void setTotal(BigDecimal total) {
            this.total = total;
        }

        public static LineItemBuilder builder() {
            return new LineItemBuilder();
        }

        public static class LineItemBuilder {
            private String serviceType;
            private String description;
            private int quantity;
            private BigDecimal total;

            LineItemBuilder() {
            }

            public LineItemBuilder serviceType(String serviceType) {
                this.serviceType = serviceType;
                return this;
            }

            public LineItemBuilder description(String description) {
                this.description = description;
                return this;
            }

            public LineItemBuilder quantity(int quantity) {
                this.quantity = quantity;
                return this;
            }

            public LineItemBuilder total(BigDecimal total) {
                this.total = total;
                return this;
            }

            public LineItem build() {
                return new LineItem(serviceType, description, quantity, total);
            }

            public String toString() {
                return "InvoiceResponse.LineItem.LineItemBuilder(serviceType=" + this.serviceType + ", description="
                        + this.description + ", quantity=" + this.quantity + ", total=" + this.total + ")";
            }
        }
    }
}
