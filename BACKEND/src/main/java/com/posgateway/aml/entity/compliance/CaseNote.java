package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.entity.User;
import jakarta.persistence.*;

import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDateTime;

/**
 * Case Note Entity
 * Represents a note or comment added to a compliance case
 */
@Entity
@Table(name = "case_notes")
@Audited
public class CaseNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ComplianceCase complianceCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = true)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private User author;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean internal; // true = internal team note, false = visible in reports

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CaseNote() {
    }

    public CaseNote(Long id, ComplianceCase complianceCase, User author, String content, boolean internal,
            LocalDateTime createdAt) {
        this.id = id;
        this.complianceCase = complianceCase;
        this.author = author;
        this.content = content;
        this.internal = internal;
        this.createdAt = createdAt;
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

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public static CaseNoteBuilder builder() {
        return new CaseNoteBuilder();
    }

    public static class CaseNoteBuilder {
        private Long id;
        private ComplianceCase complianceCase;
        private User author;
        private String content;
        private boolean internal;
        private LocalDateTime createdAt;

        CaseNoteBuilder() {
        }

        public CaseNoteBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CaseNoteBuilder complianceCase(ComplianceCase complianceCase) {
            this.complianceCase = complianceCase;
            return this;
        }

        public CaseNoteBuilder author(User author) {
            this.author = author;
            return this;
        }

        public CaseNoteBuilder content(String content) {
            this.content = content;
            return this;
        }

        public CaseNoteBuilder internal(boolean internal) {
            this.internal = internal;
            return this;
        }

        public CaseNoteBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public CaseNote build() {
            return new CaseNote(id, complianceCase, author, content, internal, createdAt);
        }

        public String toString() {
            return "CaseNote.CaseNoteBuilder(id=" + this.id + ", complianceCase=" + this.complianceCase + ", author="
                    + this.author + ", content=" + this.content + ", internal=" + this.internal + ", createdAt="
                    + this.createdAt + ")";
        }
    }
}
