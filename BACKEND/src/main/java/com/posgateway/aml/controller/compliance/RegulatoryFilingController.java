package com.posgateway.aml.controller.compliance;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.dto.compliance.FiledDocumentSummary;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.filing.RegulatoryFiledDocument;
import com.posgateway.aml.repository.ComplianceDeadlineRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.filing.FiledDocumentAccessService;
import com.posgateway.aml.service.filing.RegulatoryFilingService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Regulatory filing from Edge pulls and the filed-document store (work package R9):
 * <ul>
 *   <li>{@code POST /sar/{id}/file}: files an APPROVED SAR (the MLRO or compliance officer of the
 *       SAR's PSP); pulls the transactions with {@code filing.extract} and stores the filed
 *       document. An offline or partial Edge answers 503 with {@code retryable: true}: nothing was
 *       filed, retry.</li>
 *   <li>{@code POST /ctr/{id}/file}: the same for an open CTR_FILING compliance deadline.</li>
 *   <li>{@code GET /sar/{id}/filed-document}, {@code GET /ctr/{id}/filed-document}: the filed
 *       document's metadata.</li>
 *   <li>{@code GET /filed-documents}, {@code /filed-documents/export}: the PSP's filed-document
 *       register (metadata), paged or exported as CSV / JSON lines.</li>
 *   <li>{@code GET /filed-documents/{docId}}, {@code /filed-documents/{docId}/content}: one
 *       document's metadata, and its content (no Edge pull; audited; integrity-checked).</li>
 * </ul>
 * Tipping-off control: compliance roles only, like the SAR register.
 */
@RestController
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','MLRO','COMPLIANCE_OFFICER')")
public class RegulatoryFilingController {

    private final RegulatoryFilingService filing;
    private final FiledDocumentAccessService documents;
    private final SuspiciousActivityReportRepository sars;
    private final ComplianceDeadlineRepository deadlines;

    /** Wires the controller. */
    public RegulatoryFilingController(RegulatoryFilingService filing, FiledDocumentAccessService documents,
                                      SuspiciousActivityReportRepository sars, ComplianceDeadlineRepository deadlines) {
        this.filing = filing;
        this.documents = documents;
        this.sars = sars;
        this.deadlines = deadlines;
    }

    /** Body of a file request: the regulator's reference, when already known. */
    public record FileRequest(String regulatorReference) {}

    /** Files a SAR. */
    @PostMapping("/sar/{id}/file")
    public ResponseEntity<RegulatoryFilingService.FilingResult> fileSar(@PathVariable long id,
                                                                        @RequestBody(required = false) FileRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(filing.fileSar(id, body == null ? null : body.regulatorReference()));
    }

    /** Files a CTR deadline. */
    @PostMapping("/ctr/{id}/file")
    public ResponseEntity<RegulatoryFilingService.FilingResult> fileCtr(@PathVariable long id,
                                                                        @RequestBody(required = false) FileRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(filing.fileCtr(id, body == null ? null : body.regulatorReference()));
    }

    /** Metadata of a SAR's filed document (404 while unfiled). */
    @GetMapping("/sar/{id}/filed-document")
    public ResponseEntity<FiledDocumentSummary> sarDocument(@PathVariable long id) {
        Long psp = sars.findById(id).map(SuspiciousActivityReport::getPspId).orElse(null);
        return documents.summaryOf(RegulatoryFiledDocument.WorkflowType.SAR, id, psp == null ? -1L : psp)
                .map(s -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(s))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Metadata of a CTR deadline's filed document (404 while unfiled). */
    @GetMapping("/ctr/{id}/filed-document")
    public ResponseEntity<FiledDocumentSummary> ctrDocument(@PathVariable long id) {
        Long psp = deadlines.findById(id).map(ComplianceDeadline::getPspId).orElse(null);
        return documents.summaryOf(RegulatoryFiledDocument.WorkflowType.CTR_DEADLINE, id, psp == null ? -1L : psp)
                .map(s -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(s))
                .orElse(ResponseEntity.notFound().build());
    }

    /** The PSP's filed-document register (platform staff name the PSP). */
    @GetMapping("/filed-documents")
    public ResponseEntity<ObjectNode> register(@RequestParam(required = false) Long pspId,
                                               @RequestParam(required = false) String kind,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.register(pspId, kind, page, size));
    }

    /** The register streamed as CSV or JSON lines. */
    @GetMapping("/filed-documents/export")
    public void export(@RequestParam(required = false) Long pspId, @RequestParam(required = false) String kind,
                       @RequestParam(defaultValue = "csv") String format, HttpServletResponse response) {
        documents.exportRegister(pspId, kind, format, response);
    }

    /** One document's metadata. */
    @GetMapping("/filed-documents/{docId}")
    public ResponseEntity<FiledDocumentSummary> document(@PathVariable long docId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.summary(docId));
    }

    /** One document's content: the stored regulatory record, as filed. */
    @GetMapping("/filed-documents/{docId}/content")
    public ResponseEntity<byte[]> content(@PathVariable long docId,
                                          @RequestParam(required = false) String supportReason,
                                          @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader) {
        FiledDocumentAccessService.Download d = documents.download(docId,
                supportReason != null && !supportReason.isBlank() ? supportReason : reasonHeader);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.fileName() + "\"")
                .header("X-Content-SHA256", d.sha256())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(d.contentType()))
                .body(d.content());
    }
}
