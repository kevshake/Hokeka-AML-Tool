package com.posgateway.aml.service.cbk.projection;

/**
 * JPQL constructor-expression projection for MERCHANT_TRANSACTIONS aggregation.
 * One row per merchant, joined to Merchant for static fields.
 */
public class MerchantSettlementAggRow {

    private final String merchantId;
    private final String merchantCountry;
    private final String contactEmail;
    private final String mcc;
    private final Long count;
    private final Long valueCents;

    public MerchantSettlementAggRow(String merchantId, String merchantCountry,
                                    String contactEmail, String mcc,
                                    Long count, Long valueCents) {
        this.merchantId = merchantId;
        this.merchantCountry = merchantCountry;
        this.contactEmail = contactEmail;
        this.mcc = mcc;
        this.count = count;
        this.valueCents = valueCents;
    }

    public String getMerchantId() { return merchantId; }
    public String getMerchantCountry() { return merchantCountry; }
    public String getContactEmail() { return contactEmail; }
    public String getMcc() { return mcc; }
    public Long getCount() { return count; }
    public Long getValueCents() { return valueCents; }
}
