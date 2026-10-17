package com.posgateway.aml.service.filing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.dto.compliance.FiledDocumentSummary;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.filing.RegulatoryFiledDocument;
import com.posgateway.aml.repository.filing.RegulatoryFiledDocumentRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.edgedata.EdgeDataCaller;
import com.posgateway.aml.service.edgedata.EdgeDataCallerResolver;
import com.posgateway.aml.service.edgedata.EdgeTunnelAccess;
import com.posgateway.aml.service.edgedata.RecordStreamWriter;
import com.posgateway.aml.service.security.PspIsolationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Access to filed SAR/STR/CTR documents (R9). A filed document is the stored regulatory record, so
 * viewing or downloading it never contacts the Edge.
 *
 * <ul>
 *   <li><b>PSP-scoped.</b> A PSP user reads its own PSP's documents only (another PSP's document is
 *       "not found"). Platform staff read a document's <em>metadata</em> as for the SAR register;
 *       its <em>content</em> holds transaction data, so they need the PSP's support access and a
 *       10..500 character reason, exactly as for an Edge pull (contract §9).</li>
 *   <li><b>Audited.</b> Every content download and every register export writes an
 *       {@code audit_logs} entry (who, which document, its SHA-256, support reason).</li>
 *   <li><b>Exportable.</b> The document downloads as its original file, sealed by an
 *       {@code X-Content-SHA256} header; the register exports as CSV or JSON lines.</li>
 * </ul>
 */
@Service
public class FiledDocumentAccessService {

    /** Largest register page. */
    public static final int MAX_PAGE = 200;

    /** Register export cap. */
    static final int MAX_EXPORT_ROWS = 100_000;

    static final List<String> COLUMNS = List.of("id", "documentKind", "workflowType", "workflowId", "filingRef",
            "documentFormat", "fileName", "contentSha256", "contentLength", "transactionCount", "dataSource",
            "regulatorReference", "filedBy", "filedAt", "retainUntil");

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final RegulatoryFiledDocumentRepository repository;
    private final FiledDocumentStore store;
    private final EdgeDataCallerResolver callers;
    private final EdgeTunnelAccess tunnel;
    private final PspIsolationService isolation;
    private final AuditLogService audit;
    private final ObjectMapper mapper;
    private final Clock clock = Clock.systemUTC();

    /** Wires the access service. */
    public FiledDocumentAccessService(RegulatoryFiledDocumentRepository repository, FiledDocumentStore store,
                                      EdgeDataCallerResolver callers, EdgeTunnelAccess tunnel,
                                      PspIsolationService isolation, AuditLogService audit, ObjectMapper mapper) {
        this.repository = repository;
        this.store = store;
        this.callers = callers;
        this.tunnel = tunnel;
        this.isolation = isolation;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** A downloaded document: its bytes and headers. */
    public record Download(String fileName, String contentType, String sha256, byte[] content) {}

    // ── summaries ────────────────────────────────────────────────────────────────────────────────

    /** Metadata of the document filed from a workflow record the caller may see, if any. */
    public Optional<FiledDocumentSummary> summaryOf(RegulatoryFiledDocument.WorkflowType type, long workflowId,
                                                    Long recordPspId) {
        if (!maySeeRecordOf(recordPspId)) {
            return Optional.empty();
        }
        return repository.summaryOf(type.name(), workflowId);
    }

    /** Metadata of the documents filed from several SARs (list views); keyed by SAR id. */
    public Map<Long, FiledDocumentSummary> summariesOfSars(Collection<Long> sarIds) {
        if (sarIds == null || sarIds.isEmpty()) {
            return Map.of();
        }
        return repository.summariesOf(RegulatoryFiledDocument.WorkflowType.SAR.name(), sarIds).stream()
                .collect(Collectors.toMap(FiledDocumentSummary::workflowId, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
    }

    /** Metadata of one document by id, PSP-scoped. */
    public FiledDocumentSummary summary(long documentId) {
        long psp = repository.pspOf(documentId).orElseThrow(FiledDocumentAccessService::notFound);
        if (!maySeeRecordOf(psp)) {
            throw notFound();
        }
        return repository.summaryById(documentId).orElseThrow(FiledDocumentAccessService::notFound);
    }

    /** One register page ({@code {content, totalElements, totalPages, size, number}}). */
    public ObjectNode register(Long requestedPspId, String kind, int page, int size) {
        long psp = pspOf(requestedPspId);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE));
        Page<FiledDocumentSummary> rows = query(psp, kind, Math.max(0, page), safeSize);
        ObjectNode out = mapper.createObjectNode();
        rows.getContent().forEach(r -> out.withArray("content").add(mapper.valueToTree(r)));
        if (!out.has("content")) {
            out.putArray("content");
        }
        out.put("totalElements", rows.getTotalElements());
        out.put("totalPages", rows.getTotalPages());
        out.put("size", rows.getSize());
        out.put("number", rows.getNumber());
        return out;
    }

    /** Streams the register (metadata only) as CSV or JSON lines, audited. */
    public void exportRegister(Long requestedPspId, String kind, String rawFormat, HttpServletResponse response) {
        RecordStreamWriter.Format format = RecordStreamWriter.Format.parse(rawFormat);
        long psp = pspOf(requestedPspId);
        User user = isolation.getCurrentUser();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("pspId", psp);
        after.put("kind", kind);
        after.put("format", format.extension());
        audit.logAction(user, "FILED_DOCUMENT_REGISTER_EXPORT", "PSP", String.valueOf(psp), null, after, null, null);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(format.contentType());
        response.setHeader("Content-Disposition", "attachment; filename=\"filed-documents-" + psp + "-"
                + FILE_DATE.format(clock.instant()) + "." + format.extension() + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        RecordStreamWriter writer;
        try {
            writer = new RecordStreamWriter(response.getOutputStream(), format, COLUMNS, mapper);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        writer.start();
        int page = 0;
        while (writer.written() < MAX_EXPORT_ROWS) {
            Page<FiledDocumentSummary> rows = query(psp, kind, page++, 500);
            for (FiledDocumentSummary r : rows.getContent()) {
                if (writer.written() >= MAX_EXPORT_ROWS) {
                    break;
                }
                writer.record(mapper.valueToTree(r));
            }
            if (!rows.hasNext()) {
                break;
            }
        }
        writer.finish(mapper.createObjectNode().put("rows", writer.written()).put("incomplete", false), false, "");
    }

    // ── content ──────────────────────────────────────────────────────────────────────────────────

    /**
     * The document's content, after the PSP scope check, the support-access gate for platform staff
     * and the integrity check; audited with the document's SHA-256.
     *
     * @param supportReason platform staff only: the §9 reason for this access
     */
    public Download download(long documentId, String supportReason) {
        long psp = repository.pspOf(documentId).orElseThrow(FiledDocumentAccessService::notFound);
        EdgeDataCaller caller = callers.current();
        String reason = null;
        if (caller.pspUser()) {
            if (caller.pspId() != psp) {
                throw notFound();
            }
        } else {
            reason = supportReason == null ? null : supportReason.strip();
            boolean ok = reason != null && reason.length() >= 10 && reason.length() <= 500
                    && TunnelActor.PLATFORM_ROLES.contains(caller.role())
                    && tunnel.supportAccess().isSupportAccessEnabled(psp);
            if (!ok) {
                Map<String, Object> refused = new LinkedHashMap<>();
                refused.put("pspId", psp);
                refused.put("outcome", "SUPPORT_ACCESS_DISABLED");
                audit.logAction(isolation.getCurrentUser(), "FILED_DOCUMENT_DOWNLOAD_REFUSED",
                        "REGULATORY_FILED_DOCUMENT", String.valueOf(documentId), null, refused, null, reason);
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.SUPPORT_ACCESS_DISABLED,
                        "support access is not enabled for this PSP or the access lacks a reason"));
            }
        }
        RegulatoryFiledDocument d = store.loadVerified(documentId);
        if (d == null) {
            throw notFound();
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("pspId", psp);
        after.put("documentKind", d.getDocumentKind());
        after.put("workflowType", d.getWorkflowType());
        after.put("workflowId", d.getWorkflowId());
        after.put("contentSha256", d.getContentSha256());
        after.put("supportAccess", !caller.pspUser());
        audit.logAction(isolation.getCurrentUser(), "FILED_DOCUMENT_DOWNLOAD", "REGULATORY_FILED_DOCUMENT",
                String.valueOf(documentId), null, after, null, reason);
        return new Download(d.getFileName(), d.getContentType(), d.getContentSha256(),
                d.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** The content of the document filed from a workflow record (SAR XML export of a filed SAR). */
    public Optional<Download> downloadFor(RegulatoryFiledDocument.WorkflowType type, long workflowId,
                                          String supportReason) {
        return repository.summaryOf(type.name(), workflowId).map(s -> download(s.id(), supportReason));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** A register page of one PSP, optionally of one kind. */
    private Page<FiledDocumentSummary> query(long psp, String kind, int page, int size) {
        String k = kind == null || kind.isBlank() ? null : kind.strip().toUpperCase(Locale.ROOT);
        if (k != null && !List.of("SAR", "STR", "CTR").contains(k)) {
            throw new EdgeDataException(TunnelError.invalidParams("/kind"));
        }
        PageRequest p = PageRequest.of(page, size);
        return k == null ? repository.register(psp, p) : repository.registerOfKind(psp, k, p);
    }

    /** The PSP whose register is read: own PSP for PSP users, the named PSP for platform staff. */
    private long pspOf(Long requestedPspId) {
        EdgeDataCaller caller = callers.current();
        if (caller.pspUser()) {
            if (requestedPspId != null && !requestedPspId.equals(caller.pspId())) {
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.TENANT_MISMATCH,
                        "a PSP user can only read its own PSP"));
            }
            return caller.pspId();
        }
        if (requestedPspId == null || requestedPspId <= 0) {
            throw new EdgeDataException(TunnelError.invalidParams("/pspId"));
        }
        return requestedPspId;
    }

    /** True when the caller may see records of {@code pspId} (own PSP, or platform staff). */
    private boolean maySeeRecordOf(Long pspId) {
        EdgeDataCaller caller = callers.current();
        return !caller.pspUser() || (pspId != null && pspId.equals(caller.pspId()));
    }

    /** 404 without saying whether the document exists in another PSP. */
    private static EdgeDataException notFound() {
        return new EdgeDataException(TunnelError.of(TunnelErrorCode.NOT_FOUND, "no such filed document"));
    }
}
