package com.posgateway.aml.service.cbk.projection;

/**
 * JPQL constructor-expression projection for BILLING_TEMPLATE aggregation.
 * One row per bill_classification_code in the reporting window.
 */
public class BillingClassificationAggRow {

    private final String billClassificationCode;
    private final Long count;
    private final Long valueCents;

    public BillingClassificationAggRow(String billClassificationCode, Long count, Long valueCents) {
        this.billClassificationCode = billClassificationCode;
        this.count = count;
        this.valueCents = valueCents;
    }

    public String getBillClassificationCode() { return billClassificationCode; }
    public Long getCount() { return count; }
    public Long getValueCents() { return valueCents; }
}
