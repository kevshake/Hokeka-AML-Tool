package com.posgateway.aml.entity.monitoring;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "g2_content_scan_events")
public class G2ContentScanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(columnDefinition = "TEXT")
    private String website;

    @Column(name = "scanned_url", columnDefinition = "TEXT")
    private String scannedUrl;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "matched_keyword", length = 64)
    private String matchedKeyword;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "case_created", nullable = false)
    private boolean caseCreated;

    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt;

    @Column(name = "scanned_by", length = 128)
    private String scannedBy;

    @PrePersist
    protected void onCreate() {
        if (scannedAt == null) {
            scannedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getScannedUrl() { return scannedUrl; }
    public void setScannedUrl(String scannedUrl) { this.scannedUrl = scannedUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMatchedKeyword() { return matchedKeyword; }
    public void setMatchedKeyword(String matchedKeyword) { this.matchedKeyword = matchedKeyword; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isCaseCreated() { return caseCreated; }
    public void setCaseCreated(boolean caseCreated) { this.caseCreated = caseCreated; }
    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }
    public String getScannedBy() { return scannedBy; }
    public void setScannedBy(String scannedBy) { this.scannedBy = scannedBy; }
}
