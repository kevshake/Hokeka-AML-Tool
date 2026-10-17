package com.posgateway.aml.service.filing;

import com.posgateway.aml.edge.tunnel.Jcs;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.filing.RegulatoryFiledDocument;
import com.posgateway.aml.repository.filing.RegulatoryFiledDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The filed-document store (R9, V269): writes each filed SAR/STR/CTR document once, encrypted at
 * rest by the entity's field converter and sealed with the SHA-256 of its plaintext bytes, and reads
 * it back only after re-checking that seal. Rows are immutable (entity {@code @Immutable} plus a
 * database trigger), so a stored document can never be altered, only read.
 *
 * <p>Nothing here logs the content or any value inside it.
 */
@Service
public class FiledDocumentStore {

    private static final Logger log = LoggerFactory.getLogger(FiledDocumentStore.class);

    private final RegulatoryFiledDocumentRepository repository;

    /** Wires the store. */
    public FiledDocumentStore(RegulatoryFiledDocumentRepository repository) {
        this.repository = repository;
    }

    /**
     * A document to store.
     *
     * @param retentionYears the PSP's retention at filing time: the document is kept at least that
     *                       long from {@code filedAt} (contract §8.4)
     */
    public record NewDocument(long pspId, RegulatoryFiledDocument.Kind kind, RegulatoryFiledDocument.WorkflowType workflowType,
                              long workflowId, String filingRef, String content, FilingExtract data,
                              String regulatorReference, User filer, Instant filedAt, int retentionYears) {}

    /** The stored document failed its integrity seal (content altered or undecryptable). */
    public static class IntegrityException extends IllegalStateException {
        /** Creates the exception with a fixed message. */
        public IntegrityException(String message) {
            super(message);
        }
    }

    /**
     * Stores a document inside the caller's transaction.
     *
     * @throws FilingException {@code ALREADY_FILED} when the workflow record already has a document,
     *                         {@code FILING_STORE_FAILED} when it cannot be encrypted or written
     */
    public RegulatoryFiledDocument store(NewDocument n) {
        byte[] bytes = n.content().getBytes(StandardCharsets.UTF_8);
        RegulatoryFiledDocument d = new RegulatoryFiledDocument();
        d.setPspId(n.pspId());
        d.setDocumentKind(n.kind().name());
        d.setWorkflowType(n.workflowType().name());
        d.setWorkflowId(n.workflowId());
        d.setFilingRef(n.filingRef());
        d.setDocumentFormat(GoAmlFilingRenderer.FORMAT);
        d.setContentType(GoAmlFilingRenderer.CONTENT_TYPE);
        d.setFileName(fileName(n.kind(), n.filingRef()));
        d.setContent(n.content());
        d.setContentSha256(Jcs.sha256Hex(bytes));
        d.setContentLength((long) bytes.length);
        d.setTransactionCount(n.data().transactionCount());
        d.setDataSource(n.data().source());
        d.setEdgeAuditIds(n.data().auditIds().isEmpty() ? null
                : n.data().auditIds().stream().map(UUID::toString).collect(Collectors.joining(",")));
        d.setRegulatorReference(n.regulatorReference());
        d.setFiledByUserId(n.filer().getId());
        d.setFiledByUsername(n.filer().getUsername());
        d.setFiledAt(n.filedAt());
        d.setRetainUntil(OffsetDateTime.ofInstant(n.filedAt(), ZoneOffset.UTC).plusYears(n.retentionYears()).toInstant());
        try {
            return repository.saveAndFlush(d);
        } catch (DataIntegrityViolationException e) {
            // The failed statement aborts the transaction, so tell the cases apart by constraint name.
            String cause = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (cause.contains("uq_regulatory_filed_documents_workflow")) {
                throw new FilingException(FilingException.Code.ALREADY_FILED, "this record has already been filed",
                        n.filingRef());
            }
            log.error("Filed document for {} {} could not be stored: {}", n.workflowType(), n.workflowId(),
                    e.getClass().getSimpleName());
            throw new FilingException(FilingException.Code.FILING_STORE_FAILED,
                    "the filed document could not be stored; nothing was filed", n.filingRef());
        } catch (RuntimeException e) {
            // Typically the field-encryption key is missing: never store the document unencrypted.
            log.error("Filed document for {} {} could not be stored: {}", n.workflowType(), n.workflowId(),
                    e.getClass().getSimpleName());
            throw new FilingException(FilingException.Code.FILING_STORE_FAILED,
                    "the filed document could not be encrypted or stored; nothing was filed", n.filingRef());
        }
    }

    /**
     * Loads a document and verifies its seal.
     *
     * @throws IntegrityException when the content cannot be decrypted or no longer matches its hash
     */
    public RegulatoryFiledDocument loadVerified(long id) {
        RegulatoryFiledDocument d;
        try {
            d = repository.findById(id).orElse(null);
        } catch (RuntimeException e) {
            log.error("Filed document {} could not be decrypted: {}", id, e.getClass().getSimpleName());
            throw new IntegrityException("the filed document could not be decrypted");
        }
        if (d == null) {
            return null;
        }
        String content = d.getContent();
        String sha = content == null ? "" : Jcs.sha256Hex(content.getBytes(StandardCharsets.UTF_8));
        if (!sha.equals(d.getContentSha256())) {
            log.error("Filed document {} failed its integrity check", id);
            throw new IntegrityException("the filed document failed its integrity check");
        }
        return d;
    }

    /** Download name: {@code sar_<ref>.xml} / {@code ctr_<ref>.xml}, reference reduced to safe characters. */
    static String fileName(RegulatoryFiledDocument.Kind kind, String filingRef) {
        String safe = filingRef == null ? "document" : filingRef.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 100) {
            safe = safe.substring(0, 100);
        }
        return kind.name().toLowerCase(Locale.ROOT) + "_" + safe + ".xml";
    }
}
