package com.posgateway.aml.dto.compliance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.posgateway.aml.entity.filing.RegulatoryFiledDocument;

import java.time.Instant;

/**
 * The metadata of a filed SAR/STR/CTR document shown next to the workflow record ("filed" status)
 * and in the filed-document register. Never carries the document content: the content is served
 * only by the download endpoint, which audits each access.
 *
 * @param id                 filed-document id
 * @param documentKind       SAR, STR or CTR
 * @param workflowType       SAR or CTR_DEADLINE
 * @param workflowId         id of the SAR or the CTR deadline
 * @param filingRef          filing reference
 * @param documentFormat     e.g. GOAML_XML
 * @param contentType        MIME type
 * @param fileName           download file name
 * @param contentSha256      SHA-256 hex of the document bytes (integrity seal)
 * @param contentLength      document size in bytes
 * @param transactionCount   transactions in the document
 * @param dataSource         EDGE, CLOUD_LEGACY or NONE
 * @param regulatorReference the regulator's reference, if recorded at filing
 * @param filedBy            filing user name
 * @param filedAt            filing instant
 * @param retainUntil        retention floor of the document
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FiledDocumentSummary(Long id, String documentKind, String workflowType, Long workflowId,
                                   String filingRef, String documentFormat, String contentType, String fileName,
                                   String contentSha256, Long contentLength, Integer transactionCount,
                                   String dataSource, String regulatorReference, String filedBy, Instant filedAt,
                                   Instant retainUntil) {

    /** The summary of a stored document (content is not read). */
    public static FiledDocumentSummary of(RegulatoryFiledDocument d) {
        return new FiledDocumentSummary(d.getId(), d.getDocumentKind(), d.getWorkflowType(), d.getWorkflowId(),
                d.getFilingRef(), d.getDocumentFormat(), d.getContentType(), d.getFileName(), d.getContentSha256(),
                d.getContentLength(), d.getTransactionCount(), d.getDataSource(), d.getRegulatorReference(),
                d.getFiledByUsername(), d.getFiledAt(), d.getRetainUntil());
    }
}
