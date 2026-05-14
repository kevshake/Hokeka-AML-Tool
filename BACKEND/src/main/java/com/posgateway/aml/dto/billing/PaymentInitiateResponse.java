package com.posgateway.aml.dto.billing;

/**
 * Response returned after initiating a payment attempt.
 */
public class PaymentInitiateResponse {

    private Long attemptId;
    private String checkoutRequestId;  // populated for MPESA
    private String status;
    private String message;

    public PaymentInitiateResponse() {
    }

    public PaymentInitiateResponse(Long attemptId, String checkoutRequestId, String status, String message) {
        this.attemptId = attemptId;
        this.checkoutRequestId = checkoutRequestId;
        this.status = status;
        this.message = message;
    }

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public String getCheckoutRequestId() { return checkoutRequestId; }
    public void setCheckoutRequestId(String checkoutRequestId) { this.checkoutRequestId = checkoutRequestId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
