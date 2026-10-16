package com.posgateway.aml.entity.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class AiDailySpendId implements Serializable {

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "spend_date")
    private LocalDate spendDate;

    public AiDailySpendId() {
    }

    public AiDailySpendId(Long pspId, LocalDate spendDate) {
        this.pspId = pspId;
        this.spendDate = spendDate;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AiDailySpendId that)) return false;
        return Objects.equals(pspId, that.pspId) && Objects.equals(spendDate, that.spendDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pspId, spendDate);
    }
}
