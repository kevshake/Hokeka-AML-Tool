package com.posgateway.aml.dto.billing;

/**
 * Request body for POST /billing/payments/initiate.
 */
public class PaymentInitiateRequest {

    private Long invoiceId;

    /** MPESA or BANK_TRANSFER */
    private String paymentMethod;

    /** Kenya phone number for M-Pesa (07XXXXXXXX or 254XXXXXXXXX). */
    private String phoneNumber;

    /** Bank transfer reference number (for BANK_TRANSFER). */
    private String bankReference;

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getBankReference() { return bankReference; }
    public void setBankReference(String bankReference) { this.bankReference = bankReference; }
}
