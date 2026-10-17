package com.posgateway.aml.repository.filing;

import com.posgateway.aml.dto.compliance.FiledDocumentSummary;
import com.posgateway.aml.entity.filing.RegulatoryFiledDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Filed SAR/STR/CTR documents (V269). Insert and read only: the rows are immutable. Summary
 * queries are projections, so listing documents never reads (or decrypts) their content.
 */
public interface RegulatoryFiledDocumentRepository extends JpaRepository<RegulatoryFiledDocument, Long> {

    /** JPQL constructor of {@link FiledDocumentSummary} over alias {@code d}. */
    String SUMMARY = "SELECT new com.posgateway.aml.dto.compliance.FiledDocumentSummary(d.id, d.documentKind, "
            + "d.workflowType, d.workflowId, d.filingRef, d.documentFormat, d.contentType, d.fileName, "
            + "d.contentSha256, d.contentLength, d.transactionCount, d.dataSource, d.regulatorReference, "
            + "d.filedByUsername, d.filedAt, d.retainUntil) FROM RegulatoryFiledDocument d ";

    /** True when a workflow record already has a filed document. */
    boolean existsByWorkflowTypeAndWorkflowId(String workflowType, Long workflowId);

    /** The full document filed from one workflow record (decrypts the content). */
    Optional<RegulatoryFiledDocument> findByWorkflowTypeAndWorkflowId(String workflowType, Long workflowId);

    /** Summary of the document filed from one workflow record. */
    @Query(SUMMARY + "WHERE d.workflowType = :type AND d.workflowId = :id")
    Optional<FiledDocumentSummary> summaryOf(@Param("type") String workflowType, @Param("id") Long workflowId);

    /** Summary of a document by id. */
    @Query(SUMMARY + "WHERE d.id = :id")
    Optional<FiledDocumentSummary> summaryById(@Param("id") Long id);

    /** Summaries of the documents of several workflow records of one type (list views). */
    @Query(SUMMARY + "WHERE d.workflowType = :type AND d.workflowId IN :ids")
    List<FiledDocumentSummary> summariesOf(@Param("type") String workflowType, @Param("ids") Collection<Long> workflowIds);

    /** The PSP owning a document (tenant check before anything else is read). */
    @Query("SELECT d.pspId FROM RegulatoryFiledDocument d WHERE d.id = :id")
    Optional<Long> pspOf(@Param("id") Long id);

    /** A PSP's filed-document register, newest first. */
    @Query(value = SUMMARY + "WHERE d.pspId = :pspId ORDER BY d.filedAt DESC, d.id DESC",
            countQuery = "SELECT COUNT(d) FROM RegulatoryFiledDocument d WHERE d.pspId = :pspId")
    Page<FiledDocumentSummary> register(@Param("pspId") Long pspId, Pageable pageable);

    /** A PSP's filed-document register of one kind, newest first. */
    @Query(value = SUMMARY + "WHERE d.pspId = :pspId AND d.documentKind = :kind ORDER BY d.filedAt DESC, d.id DESC",
            countQuery = "SELECT COUNT(d) FROM RegulatoryFiledDocument d WHERE d.pspId = :pspId AND d.documentKind = :kind")
    Page<FiledDocumentSummary> registerOfKind(@Param("pspId") Long pspId, @Param("kind") String kind,
                                              Pageable pageable);
}
