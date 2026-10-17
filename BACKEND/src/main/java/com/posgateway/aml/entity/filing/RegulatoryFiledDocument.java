package com.posgateway.aml.entity.filing;

import com.posgateway.aml.entity.converter.VersionedAesGcmStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A filed SAR/STR/CTR document ({@code regulatory_filed_documents}, V269): the regulatory record and
 * the only cloud row allowed to hold class-T transaction data (residency contract §2.3). The
 * rendered document is encrypted at rest with the platform field key
 * ({@link VersionedAesGcmStringConverter}) and sealed by {@link #contentSha256} over its plaintext
 * bytes.
 *
 * <p>Immutable: Hibernate never updates it ({@link Immutable}) and a database trigger refuses any
 * UPDATE, and any DELETE before {@link #retainUntil}. Deliberately not {@code @Audited}: the
 * document must not be copied into an Envers mirror (contract §7.16); filing and every access are
 * audited through {@code audit_logs}.
 */
@Entity
@Immutable
@Table(name = "regulatory_filed_documents")
public class RegulatoryFiledDocument {

    /** Filing kinds the filed-document store accepts. */
    public enum Kind { SAR, STR, CTR }

    /** Workflow record a document was filed from. */
    public enum WorkflowType { SAR, CTR_DEADLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false, updatable = false)
    private Long pspId;

    @Column(name = "document_kind", nullable = false, length = 8, updatable = false)
    private String documentKind;

    @Column(name = "workflow_type", nullable = false, length = 24, updatable = false)
    private String workflowType;

    @Column(name = "workflow_id", nullable = false, updatable = false)
    private Long workflowId;

    @Column(name = "filing_ref", nullable = false, length = 64, updatable = false)
    private String filingRef;

    @Column(name = "document_format", nullable = false, length = 24, updatable = false)
    private String documentFormat;

    @Column(name = "content_type", nullable = false, length = 64, updatable = false)
    private String contentType;

    @Column(name = "file_name", nullable = false, length = 128, updatable = false)
    private String fileName;

    /** The rendered document (plaintext in memory, AES-256-GCM {@code enc:v1:} in the column). */
    @Convert(converter = VersionedAesGcmStringConverter.class)
    @Column(name = "content_ciphertext", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String content;

    // V269 declares CHAR(64) (bpchar): map it as CHAR so ddl-auto=validate matches.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false)
    private String contentSha256;

    @Column(name = "content_length", nullable = false, updatable = false)
    private Long contentLength;

    @Column(name = "transaction_count", nullable = false, updatable = false)
    private Integer transactionCount;

    @Column(name = "data_source", nullable = false, length = 16, updatable = false)
    private String dataSource;

    @Column(name = "edge_audit_ids", columnDefinition = "TEXT", updatable = false)
    private String edgeAuditIds;

    @Column(name = "regulator_reference", length = 128, updatable = false)
    private String regulatorReference;

    @Column(name = "filed_by_user_id", nullable = false, updatable = false)
    private Long filedByUserId;

    @Column(name = "filed_by_username", length = 255, updatable = false)
    private String filedByUsername;

    @Column(name = "filed_at", nullable = false, updatable = false)
    private Instant filedAt;

    @Column(name = "retain_until", nullable = false, updatable = false)
    private Instant retainUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA constructor. */
    public RegulatoryFiledDocument() {
    }

    /** Stamps the creation time on insert. */
    @jakarta.persistence.PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Database id. */
    public Long getId() { return id; }
    /** Sets the id (tests). */
    public void setId(Long id) { this.id = id; }
    /** Owning PSP. */
    public Long getPspId() { return pspId; }
    /** Sets the owning PSP. */
    public void setPspId(Long pspId) { this.pspId = pspId; }
    /** SAR, STR or CTR. */
    public String getDocumentKind() { return documentKind; }
    /** Sets the kind. */
    public void setDocumentKind(String documentKind) { this.documentKind = documentKind; }
    /** SAR or CTR_DEADLINE. */
    public String getWorkflowType() { return workflowType; }
    /** Sets the workflow type. */
    public void setWorkflowType(String workflowType) { this.workflowType = workflowType; }
    /** Id of the SAR or the CTR compliance deadline. */
    public Long getWorkflowId() { return workflowId; }
    /** Sets the workflow id. */
    public void setWorkflowId(Long workflowId) { this.workflowId = workflowId; }
    /** The filing reference (SAR reference, {@code CTR-<deadlineId>}). */
    public String getFilingRef() { return filingRef; }
    /** Sets the filing reference. */
    public void setFilingRef(String filingRef) { this.filingRef = filingRef; }
    /** Rendering format, e.g. {@code GOAML_XML}. */
    public String getDocumentFormat() { return documentFormat; }
    /** Sets the format. */
    public void setDocumentFormat(String documentFormat) { this.documentFormat = documentFormat; }
    /** MIME type of the content. */
    public String getContentType() { return contentType; }
    /** Sets the MIME type. */
    public void setContentType(String contentType) { this.contentType = contentType; }
    /** Download file name. */
    public String getFileName() { return fileName; }
    /** Sets the file name. */
    public void setFileName(String fileName) { this.fileName = fileName; }
    /** The decrypted document. */
    public String getContent() { return content; }
    /** Sets the document (encrypted on write). */
    public void setContent(String content) { this.content = content; }
    /** SHA-256 hex of the plaintext UTF-8 bytes. */
    public String getContentSha256() { return contentSha256; }
    /** Sets the content hash. */
    public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }
    /** Plaintext length in bytes. */
    public Long getContentLength() { return contentLength; }
    /** Sets the plaintext length. */
    public void setContentLength(Long contentLength) { this.contentLength = contentLength; }
    /** Transactions included in the document. */
    public Integer getTransactionCount() { return transactionCount; }
    /** Sets the transaction count. */
    public void setTransactionCount(Integer transactionCount) { this.transactionCount = transactionCount; }
    /** Where the transaction data came from: EDGE, CLOUD_LEGACY or NONE. */
    public String getDataSource() { return dataSource; }
    /** Sets the data source. */
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    /** Comma-separated {@code edge_data_access_log.audit_id}s of the filing pulls. */
    public String getEdgeAuditIds() { return edgeAuditIds; }
    /** Sets the access-log ids. */
    public void setEdgeAuditIds(String edgeAuditIds) { this.edgeAuditIds = edgeAuditIds; }
    /** Regulator's reference for the filing, if given at filing time. */
    public String getRegulatorReference() { return regulatorReference; }
    /** Sets the regulator reference. */
    public void setRegulatorReference(String regulatorReference) { this.regulatorReference = regulatorReference; }
    /** Filing user id. */
    public Long getFiledByUserId() { return filedByUserId; }
    /** Sets the filing user id. */
    public void setFiledByUserId(Long filedByUserId) { this.filedByUserId = filedByUserId; }
    /** Filing user name. */
    public String getFiledByUsername() { return filedByUsername; }
    /** Sets the filing user name. */
    public void setFiledByUsername(String filedByUsername) { this.filedByUsername = filedByUsername; }
    /** Filing instant. */
    public Instant getFiledAt() { return filedAt; }
    /** Sets the filing instant. */
    public void setFiledAt(Instant filedAt) { this.filedAt = filedAt; }
    /** Retention floor (filing date + retention years). */
    public Instant getRetainUntil() { return retainUntil; }
    /** Sets the retention floor. */
    public void setRetainUntil(Instant retainUntil) { this.retainUntil = retainUntil; }
    /** Row creation instant. */
    public Instant getCreatedAt() { return createdAt; }
    /** Sets the creation instant. */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
