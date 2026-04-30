package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.entity.User;
import jakarta.persistence.*;

import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDateTime;

/**
 * Case Evidence Entity
 * Represents a file or document attached to a compliance case
 */
@Entity
@Table(name = "case_evidence")
@Audited
public class CaseEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ComplianceCase complianceCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private User uploadedBy;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileType; // PDF, JPG, etc.

    @Column(nullable = false)
    private String storagePath; // Path or URL to stored file

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    public CaseEvidence() {
    }

    public CaseEvidence(Long id, ComplianceCase complianceCase, User uploadedBy, String fileName, String fileType,
            String storagePath, String description, LocalDateTime uploadedAt) {
        this.id = id;
        this.complianceCase = complianceCase;
        this.uploadedBy = uploadedBy;
        this.fileName = fileName;
        this.fileType = fileType;
        this.storagePath = storagePath;
        this.description = description;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ComplianceCase getComplianceCase() {
        return complianceCase;
    }

    public void setComplianceCase(ComplianceCase complianceCase) {
        this.complianceCase = complianceCase;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(User uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    public static CaseEvidenceBuilder builder() {
        return new CaseEvidenceBuilder();
    }

    public static class CaseEvidenceBuilder {
        private Long id;
        private ComplianceCase complianceCase;
        private User uploadedBy;
        private String fileName;
        private String fileType;
        private String storagePath;
        private String description;
        private LocalDateTime uploadedAt;

        CaseEvidenceBuilder() {
        }

        public CaseEvidenceBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CaseEvidenceBuilder complianceCase(ComplianceCase complianceCase) {
            this.complianceCase = complianceCase;
            return this;
        }

        public CaseEvidenceBuilder uploadedBy(User uploadedBy) {
            this.uploadedBy = uploadedBy;
            return this;
        }

        public CaseEvidenceBuilder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public CaseEvidenceBuilder fileType(String fileType) {
            this.fileType = fileType;
            return this;
        }

        public CaseEvidenceBuilder storagePath(String storagePath) {
            this.storagePath = storagePath;
            return this;
        }

        public CaseEvidenceBuilder description(String description) {
            this.description = description;
            return this;
        }

        public CaseEvidenceBuilder uploadedAt(LocalDateTime uploadedAt) {
            this.uploadedAt = uploadedAt;
            return this;
        }

        public CaseEvidence build() {
            return new CaseEvidence(id, complianceCase, uploadedBy, fileName, fileType, storagePath, description,
                    uploadedAt);
        }

        public String toString() {
            return "CaseEvidence.CaseEvidenceBuilder(id=" + this.id + ", complianceCase=" + this.complianceCase
                    + ", uploadedBy=" + this.uploadedBy + ", fileName=" + this.fileName + ", fileType=" + this.fileType
                    + ", storagePath=" + this.storagePath + ", description=" + this.description + ", uploadedAt="
                    + this.uploadedAt + ")";
        }
    }
}
