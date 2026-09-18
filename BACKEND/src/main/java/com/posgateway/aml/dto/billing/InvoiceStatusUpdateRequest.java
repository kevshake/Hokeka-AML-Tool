package com.posgateway.aml.dto.billing;

import java.math.BigDecimal;

/**
 * Request body for updating invoice status (mark-paid, mark-sent, cancel).
 */
public class InvoiceStatusUpdateRequest {

    /** Target status: PAID, SENT, CANCELLED, OVERDUE */
    private String status;

    private String paymentReference;
    private String paymentMethod;
    private BigDecimal paymentAmount;

    public InvoiceStatusUpdateRequest() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }
}
