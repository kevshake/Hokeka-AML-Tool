package com.posgateway.aml.integration.mpesa;

/**
 * Holds the relevant fields returned by the Daraja STK Push API.
 */
public class MpesaStkResponse {

    private String merchantRequestId;
    private String checkoutRequestId;
    private String responseCode;
    private String responseDescription;
    private String customerMessage;

    public MpesaStkResponse() {
    }

    public MpesaStkResponse(String merchantRequestId, String checkoutRequestId,
                             String responseCode, String responseDescription, String customerMessage) {
        this.merchantRequestId = merchantRequestId;
        this.checkoutRequestId = checkoutRequestId;
        this.responseCode = responseCode;
        this.responseDescription = responseDescription;
        this.customerMessage = customerMessage;
    }

    public boolean isSuccess() {
        return "0".equals(responseCode);
    }

    // ─── Getters / Setters ───────────────────────────────────────────────────

    public String getMerchantRequestId() { return merchantRequestId; }
    public void setMerchantRequestId(String merchantRequestId) { this.merchantRequestId = merchantRequestId; }

    public String getCheckoutRequestId() { return checkoutRequestId; }
    public void setCheckoutRequestId(String checkoutRequestId) { this.checkoutRequestId = checkoutRequestId; }

    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

    public String getResponseDescription() { return responseDescription; }
    public void setResponseDescription(String responseDescription) { this.responseDescription = responseDescription; }

    public String getCustomerMessage() { return customerMessage; }
    public void setCustomerMessage(String customerMessage) { this.customerMessage = customerMessage; }
}
