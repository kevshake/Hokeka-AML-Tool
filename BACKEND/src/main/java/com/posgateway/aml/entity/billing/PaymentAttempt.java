package com.posgateway.aml.entity.billing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Tracks a single payment attempt against an invoice.
 * <p>
 * Status lifecycle:
 * <ul>
 *   <li>PENDING — attempt created, STK push sent (or bank reference recorded)</li>
 *   <li>PROCESSING — callback received, under verification</li>
 *   <li>SUCCESS — payment confirmed</li>
 *   <li>FAILED — Daraja returned non-zero ResultCode or timeout</li>
 *   <li>CANCELLED — user cancelled the STK push on their phone</li>
 * </ul>
 * paymentMethod is one of: MPESA, BANK_TRANSFER, CARD.
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "KES";

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "bank_reference", length = 100)
    private String bankReference;

    @Column(name = "mpesa_checkout_request_id", length = 100)
    private String mpesaCheckoutRequestId;

    @Column(name = "mpesa_merchant_request_id", length = 100)
    private String mpesaMerchantRequestId;

    @Column(name = "mpesa_transaction_id", length = 100)
    private String mpesaTransactionId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "result_code", length = 10)
    private String resultCode;

    @Column(name = "result_description", columnDefinition = "text")
    private String resultDescription;

    @Column(name = "initiated_at", nullable = false)
    private OffsetDateTime initiatedAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public PaymentAttempt() {
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ─── Getters / Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getBankReference() { return bankReference; }
    public void setBankReference(String bankReference) { this.bankReference = bankReference; }

    public String getMpesaCheckoutRequestId() { return mpesaCheckoutRequestId; }
    public void setMpesaCheckoutRequestId(String mpesaCheckoutRequestId) { this.mpesaCheckoutRequestId = mpesaCheckoutRequestId; }

    public String getMpesaMerchantRequestId() { return mpesaMerchantRequestId; }
    public void setMpesaMerchantRequestId(String mpesaMerchantRequestId) { this.mpesaMerchantRequestId = mpesaMerchantRequestId; }

    public String getMpesaTransactionId() { return mpesaTransactionId; }
    public void setMpesaTransactionId(String mpesaTransactionId) { this.mpesaTransactionId = mpesaTransactionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }

    public String getResultDescription() { return resultDescription; }
    public void setResultDescription(String resultDescription) { this.resultDescription = resultDescription; }

    public OffsetDateTime getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(OffsetDateTime initiatedAt) { this.initiatedAt = initiatedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
