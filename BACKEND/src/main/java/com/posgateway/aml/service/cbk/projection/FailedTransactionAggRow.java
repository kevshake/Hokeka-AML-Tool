package com.posgateway.aml.service.cbk.projection;

/**
 * JPQL constructor-expression projection for FAILED_TRANSACTIONS aggregation.
 * One row per (merchant_id, acquirer_response) combination in the window.
 * The reason field holds acquirerResponse text (truncated to 100 chars),
 * or "TRRC99" when acquirerResponse is null.
 */
public class FailedTransactionAggRow {

    private final String merchantId;
    private final String reason;
    private final Long count;
    private final Long valueCents;

    public FailedTransactionAggRow(String merchantId, String reason, Long count, Long valueCents) {
        this.merchantId = merchantId;
        this.reason = reason;
        this.count = count;
        this.valueCents = valueCents;
    }

    public String getMerchantId() { return merchantId; }
    public String getReason() { return reason; }
    public Long getCount() { return count; }
    public Long getValueCents() { return valueCents; }
}
