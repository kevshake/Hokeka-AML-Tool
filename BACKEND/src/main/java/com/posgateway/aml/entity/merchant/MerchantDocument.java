package com.posgateway.aml.entity.merchant;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "merchant_documents")
public class MerchantDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long documentId;

    @Column(nullable = false)
    private Long merchantId;

    @Column(nullable = false)
    private String documentType; // e.g., PASSPORT, LICENSE, UTILITY_BILL

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String status; // PENDING, VERIFIED, REJECTED

    private java.time.LocalDate expiryDate;

    private LocalDateTime uploadedAt;
    private LocalDateTime verifiedAt;

    // Version Control Fields
    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "previous_version_id")
    private Long previousVersionId;

    @Column(name = "is_current_version")
    private Boolean isCurrentVersion = true;

    public MerchantDocument() {
    }

    public MerchantDocument(Long documentId, Long merchantId, String documentType, String filePath, String fileName,
            String status, java.time.LocalDate expiryDate, LocalDateTime uploadedAt, LocalDateTime verifiedAt) {
        this.documentId = documentId;
        this.merchantId = merchantId;
        this.documentType = documentType;
        this.filePath = filePath;
        this.fileName = fileName;
        this.status = status;
        this.expiryDate = expiryDate;
        this.uploadedAt = uploadedAt;
        this.verifiedAt = verifiedAt;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public java.time.LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(java.time.LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getPreviousVersionId() {
        return previousVersionId;
    }

    public void setPreviousVersionId(Long previousVersionId) {
        this.previousVersionId = previousVersionId;
    }

    public Boolean getIsCurrentVersion() {
        return isCurrentVersion;
    }

    public void setIsCurrentVersion(Boolean isCurrentVersion) {
        this.isCurrentVersion = isCurrentVersion;
    }

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
        if (version == null) {
            version = 1;
        }
        if (isCurrentVersion == null) {
            isCurrentVersion = true;
        }
    }
}
