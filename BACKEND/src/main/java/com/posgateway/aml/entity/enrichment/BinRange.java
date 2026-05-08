package com.posgateway.aml.entity.enrichment;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Card BIN-prefix → metadata row backing the {@code bin_ranges} table
 * (created in V130). The natural primary key is the BIN prefix
 * (6-8 digit string); longer prefixes win on lookup.
 *
 * <p>Per the no-mock rule the seed only carries rule-based brand mappings;
 * issuer / issuer_country are NULL until populated by a real BIN provider.
 */
@Entity
@Table(name = "bin_ranges")
public class BinRange {

    @Id
    @Column(name = "bin_prefix", nullable = false, length = 8)
    private String binPrefix;

    @Column(name = "issuer", length = 255)
    private String issuer;

    @Column(name = "issuer_country", length = 2)
    private String issuerCountry;

    @Column(name = "card_brand", length = 32)
    private String cardBrand;

    /** CREDIT / DEBIT / PREPAID */
    @Column(name = "card_type", length = 16)
    private String cardType;

    /** CLASSIC / PLATINUM / BUSINESS / etc. */
    @Column(name = "card_class", length = 32)
    private String cardClass;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public BinRange() {}

    public String getBinPrefix() { return binPrefix; }
    public void setBinPrefix(String binPrefix) { this.binPrefix = binPrefix; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getIssuerCountry() { return issuerCountry; }
    public void setIssuerCountry(String issuerCountry) { this.issuerCountry = issuerCountry; }
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getCardClass() { return cardClass; }
    public void setCardClass(String cardClass) { this.cardClass = cardClass; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
