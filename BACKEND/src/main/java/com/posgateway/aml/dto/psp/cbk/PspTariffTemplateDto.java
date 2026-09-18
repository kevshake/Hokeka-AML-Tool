package com.posgateway.aml.dto.psp.cbk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PspTariffTemplateDto {

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

    public PspTariffTemplateDto() {}

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
}
