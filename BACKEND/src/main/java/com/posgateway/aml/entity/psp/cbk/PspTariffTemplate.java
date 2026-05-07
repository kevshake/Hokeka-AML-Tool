package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CBK GDI #15 – Payment Gateway Tariff Templates (monthly).
 * Maps to table psp_tariff_templates.
 */
@Entity
@Table(name = "psp_tariff_templates", indexes = {
        @Index(name = "idx_psp_tariff_templates_psp_id", columnList = "psp_id")
})
public class PspTariffTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "channel_used", length = 128)
    private String channelUsed;

    @Column(name = "channel_partner_name", length = 256)
    private String channelPartnerName;

    @Column(name = "charge_description", columnDefinition = "TEXT")
    private String chargeDescription;

    @Column(name = "percentage_transaction_cost", precision = 7, scale = 4)
    private BigDecimal percentageTransactionCost;

    @Column(name = "absolute_transaction_cost", precision = 18, scale = 4)
    private BigDecimal absoluteTransactionCost;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspTariffTemplate() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getChannelUsed() { return channelUsed; }
    public void setChannelUsed(String channelUsed) { this.channelUsed = channelUsed; }

    public String getChannelPartnerName() { return channelPartnerName; }
    public void setChannelPartnerName(String channelPartnerName) { this.channelPartnerName = channelPartnerName; }

    public String getChargeDescription() { return chargeDescription; }
    public void setChargeDescription(String chargeDescription) { this.chargeDescription = chargeDescription; }

    public BigDecimal getPercentageTransactionCost() { return percentageTransactionCost; }
    public void setPercentageTransactionCost(BigDecimal percentageTransactionCost) { this.percentageTransactionCost = percentageTransactionCost; }

    public BigDecimal getAbsoluteTransactionCost() { return absoluteTransactionCost; }
    public void setAbsoluteTransactionCost(BigDecimal absoluteTransactionCost) { this.absoluteTransactionCost = absoluteTransactionCost; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static PspTariffTemplateBuilder builder() { return new PspTariffTemplateBuilder(); }

    public static class PspTariffTemplateBuilder {
        private Long id;
        private Long pspId;
        private String channelUsed;
        private String channelPartnerName;
        private String chargeDescription;
        private BigDecimal percentageTransactionCost;
        private BigDecimal absoluteTransactionCost;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        PspTariffTemplateBuilder() {}

        public PspTariffTemplateBuilder id(Long id) { this.id = id; return this; }
        public PspTariffTemplateBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspTariffTemplateBuilder channelUsed(String channelUsed) { this.channelUsed = channelUsed; return this; }
        public PspTariffTemplateBuilder channelPartnerName(String channelPartnerName) { this.channelPartnerName = channelPartnerName; return this; }
        public PspTariffTemplateBuilder chargeDescription(String chargeDescription) { this.chargeDescription = chargeDescription; return this; }
        public PspTariffTemplateBuilder percentageTransactionCost(BigDecimal percentageTransactionCost) { this.percentageTransactionCost = percentageTransactionCost; return this; }
        public PspTariffTemplateBuilder absoluteTransactionCost(BigDecimal absoluteTransactionCost) { this.absoluteTransactionCost = absoluteTransactionCost; return this; }
        public PspTariffTemplateBuilder effectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; return this; }
        public PspTariffTemplateBuilder effectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; return this; }
        public PspTariffTemplateBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspTariffTemplateBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspTariffTemplate build() {
            PspTariffTemplate e = new PspTariffTemplate();
            e.id = this.id;
            e.pspId = this.pspId;
            e.channelUsed = this.channelUsed;
            e.channelPartnerName = this.channelPartnerName;
            e.chargeDescription = this.chargeDescription;
            e.percentageTransactionCost = this.percentageTransactionCost;
            e.absoluteTransactionCost = this.absoluteTransactionCost;
            e.effectiveFrom = this.effectiveFrom;
            e.effectiveTo = this.effectiveTo;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
