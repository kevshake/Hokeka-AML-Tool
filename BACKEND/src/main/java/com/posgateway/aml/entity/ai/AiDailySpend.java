package com.posgateway.aml.entity.ai;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ai_daily_spend")
@IdClass(AiDailySpendId.class)
public class AiDailySpend {

    @Id
    @Column(name = "psp_id")
    private Long pspId;

    @Id
    @Column(name = "spend_date")
    private LocalDate spendDate;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    @Column(name = "total_tokens", nullable = false)
    private long totalTokens;

    @Column(name = "estimated_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedUsd = BigDecimal.ZERO;

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public LocalDate getSpendDate() {
        return spendDate;
    }

    public void setSpendDate(LocalDate spendDate) {
        this.spendDate = spendDate;
    }

    public int getCallCount() {
        return callCount;
    }

    public void setCallCount(int callCount) {
        this.callCount = callCount;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public BigDecimal getEstimatedUsd() {
        return estimatedUsd;
    }

    public void setEstimatedUsd(BigDecimal estimatedUsd) {
        this.estimatedUsd = estimatedUsd;
    }
}
