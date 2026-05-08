package com.posgateway.aml.service.cbk.projection;

/**
 * JPQL constructor-expression projection for TRANSACTION_DETAILS aggregation.
 * One row per (card_brand, card_type, card_class, channel_type) combination.
 */
public class TransactionDetailAggRow {

    private final String cardBrand;
    private final String cardType;
    private final String cardClass;
    private final String channelType;
    private final Long count;
    private final Long valueCents;

    public TransactionDetailAggRow(String cardBrand, String cardType, String cardClass,
                                   String channelType, Long count, Long valueCents) {
        this.cardBrand = cardBrand;
        this.cardType = cardType;
        this.cardClass = cardClass;
        this.channelType = channelType;
        this.count = count;
        this.valueCents = valueCents;
    }

    public String getCardBrand() { return cardBrand; }
    public String getCardType() { return cardType; }
    public String getCardClass() { return cardClass; }
    public String getChannelType() { return channelType; }
    public Long getCount() { return count; }
    public Long getValueCents() { return valueCents; }
}
