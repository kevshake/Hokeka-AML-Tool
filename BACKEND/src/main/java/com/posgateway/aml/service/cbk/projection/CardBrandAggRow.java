package com.posgateway.aml.service.cbk.projection;

/**
 * JPQL constructor-expression projection for CARD_BRANDS aggregation.
 * One row per card_brand value in the reporting window.
 */
public class CardBrandAggRow {

    private final String cardBrand;
    private final Long count;
    private final Long valueCents;

    public CardBrandAggRow(String cardBrand, Long count, Long valueCents) {
        this.cardBrand = cardBrand;
        this.count = count;
        this.valueCents = valueCents;
    }

    public String getCardBrand() { return cardBrand; }
    public Long getCount() { return count; }
    public Long getValueCents() { return valueCents; }
}
