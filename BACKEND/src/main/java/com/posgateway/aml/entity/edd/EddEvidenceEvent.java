package com.posgateway.aml.entity.edd;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "edd_evidence_events", indexes = {
        @Index(name = "idx_edd_evidence_request", columnList = "edd_request_id, occurred_at"),
        @Index(name = "idx_edd_evidence_merchant", columnList = "merchant_id, occurred_at")
})
public class EddEvidenceEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "edd_request_id", nullable = false)
    private Long eddRequestId;
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;
    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;
    @Column(name = "previous_value")
    private Boolean previousValue;
    @Column(name = "new_value", nullable = false)
    private Boolean newValue;
    @Column(name = "performed_by", nullable = false, length = 200)
    private String performedBy;
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEddRequestId() { return eddRequestId; }
    public void setEddRequestId(Long value) { this.eddRequestId = value; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long value) { this.merchantId = value; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String value) { this.itemCode = value; }
    public Boolean getPreviousValue() { return previousValue; }
    public void setPreviousValue(Boolean value) { this.previousValue = value; }
    public Boolean getNewValue() { return newValue; }
    public void setNewValue(Boolean value) { this.newValue = value; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String value) { this.performedBy = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { this.notes = value; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime value) { this.occurredAt = value; }
}
