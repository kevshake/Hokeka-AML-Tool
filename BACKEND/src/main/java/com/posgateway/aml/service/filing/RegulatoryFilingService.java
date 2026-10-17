package com.posgateway.aml.service.filing;

import com.posgateway.aml.dto.compliance.FiledDocumentSummary;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.filing.LegalHoldWorkflow;
import com.posgateway.aml.entity.filing.RegulatoryFiledDocument;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceDeadlineRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.filing.RegulatoryFiledDocumentRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.PermissionService;
import com.posgateway.aml.service.SarWorkflowService;
import com.posgateway.aml.service.edgedata.EdgeDataCallerResolver;
import com.posgateway.aml.service.edgedata.EdgeDataScope;
import com.posgateway.aml.service.legalhold.LegalHoldService;
import com.posgateway.aml.service.residency.PspDataSettingsService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * SAR/STR and CTR filing from Edge pulls (work package R9; residency plan conflicts 2 and 7,
 * contract §2.3): {@code POST /api/v1/sar/{id}/file} and {@code POST /api/v1/ctr/{id}/file}.
 *
 * <p>A filing runs in three steps so no database lock is held while the Edge is asked:
 * <ol>
 *   <li><b>Check</b> (read-only): the record exists in the caller's PSP; the caller is that PSP's
 *       MLRO or compliance officer with {@code FILE_SAR}; a SAR is {@code APPROVED} (maker-checker
 *       of the SAR workflow), a CTR deadline is open; nothing has been filed for it yet.</li>
 *   <li><b>Pull</b>: {@code filing.extract} for the record's transactions as the filer (purpose
 *       SAR_FILING / CTR_FILING, audited in {@code edge_data_access_log} with the filing reference).
 *       An offline, partial or failing Edge refuses the filing with a retryable error: an incomplete
 *       document is never filed and nothing changes, so the filer simply retries.</li>
 *   <li><b>File</b> (one transaction, record row locked): re-check the state, render the document
 *       with the full transaction details, store it immutably (encrypted, SHA-256 sealed), move the
 *       record to FILED / completed, cover its transactions with a legal hold whose release basis is
 *       the filing date, and write the audit entries.</li>
 * </ol>
 *
 * <p><b>Tipping-off.</b> Filing produces no customer- or merchant-facing output of any kind: no
 * webhook, no notification, no message. The filed document and the filed status are served only
 * through the compliance-role SAR endpoints, and outbound customer copy stays behind
 * {@code TippingOffGuard}. The only thing that leaves the cloud is the hold notify to the PSP's own
 * Edge (hold id, reason code and reference, contract §7.10).
 */
@Service
public class RegulatoryFilingService {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryFilingService.class);

    /** Roles that may file for their PSP (the MLRO and compliance officers). */
    static final Set<String> FILER_ROLES = Set.of("MLRO", "COMPLIANCE_OFFICER");

    /** Accepted regulator references: printable, at most 128 characters. */
    private static final Pattern REGULATOR_REF = Pattern.compile("^[\\x21-\\x7E][\\x20-\\x7E]{0,127}$");

    /** {@code common.schema.json#/$defs/workflowRef}. */
    private static final Pattern WORKFLOW_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");

    /** Outcome of a filing. */
    public record FilingResult(String workflowType, Long workflowId, String status, FiledDocumentSummary document) {}

    private final SuspiciousActivityReportRepository sars;
    private final ComplianceDeadlineRepository deadlines;
    private final AlertRepository alerts;
    private final RegulatoryFiledDocumentRepository documents;
    private final SarWorkflowService sarWorkflow;
    private final PermissionService permissions;
    private final PspIsolationService isolation;
    private final EdgeDataCallerResolver callers;
    private final FilingDataService data;
    private final GoAmlFilingRenderer renderer;
    private final FiledDocumentStore store;
    private final LegalHoldService holds;
    private final PspDataSettingsService dataSettings;
    private final AuditLogService audit;
    private final TransactionTemplate tx;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    /** Wires the filing service. */
    @Autowired
    public RegulatoryFilingService(SuspiciousActivityReportRepository sars, ComplianceDeadlineRepository deadlines,
                                   AlertRepository alerts, RegulatoryFiledDocumentRepository documents,
                                   SarWorkflowService sarWorkflow, PermissionService permissions,
                                   PspIsolationService isolation, EdgeDataCallerResolver callers,
                                   FilingDataService data, GoAmlFilingRenderer renderer, FiledDocumentStore store,
                                   LegalHoldService holds, PspDataSettingsService dataSettings, AuditLogService audit,
                                   PlatformTransactionManager transactionManager) {
        this(sars, deadlines, alerts, documents, sarWorkflow, permissions, isolation, callers, data, renderer, store,
                holds, dataSettings, audit, transactionManager, Clock.systemUTC());
    }

    /** Test constructor with an explicit clock. */
    public RegulatoryFilingService(SuspiciousActivityReportRepository sars, ComplianceDeadlineRepository deadlines,
                                   AlertRepository alerts, RegulatoryFiledDocumentRepository documents,
                                   SarWorkflowService sarWorkflow, PermissionService permissions,
                                   PspIsolationService isolation, EdgeDataCallerResolver callers,
                                   FilingDataService data, GoAmlFilingRenderer renderer, FiledDocumentStore store,
                                   LegalHoldService holds, PspDataSettingsService dataSettings, AuditLogService audit,
                                   PlatformTransactionManager transactionManager, Clock clock) {
        this.sars = sars;
        this.deadlines = deadlines;
        this.alerts = alerts;
        this.documents = documents;
        this.sarWorkflow = sarWorkflow;
        this.permissions = permissions;
        this.isolation = isolation;
        this.callers = callers;
        this.data = data;
        this.renderer = renderer;
        this.store = store;
        this.holds = holds;
        this.dataSettings = dataSettings;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    // ── SAR / STR ────────────────────────────────────────────────────────────────────────────────

    /** What the check step learned about a SAR. */
    private record SarSnapshot(long pspId, String filingRef, String kind, List<TransactionReference> refs) {}

    /**
     * Files an APPROVED SAR: pulls its transactions from the Edge, stores the filed document and
     * marks the SAR FILED.
     *
     * @param regulatorReference the regulator's reference for the submission, if already known
     * @throws FilingException when the filing is refused (nothing changed)
     */
    public FilingResult fileSar(long sarId, String regulatorReference) {
        User filer = currentUser();
        String regRef = regulatorRef(regulatorReference);
        SarSnapshot snap = readOnly.execute(s -> {
            SuspiciousActivityReport sar = sars.findById(sarId).orElse(null);
            if (sar == null || sar.getPspId() == null) {
                throw new FilingException(FilingException.Code.FILING_NOT_FOUND, "no such SAR", null);
            }
            String ref = filingRef(sar);
            requireFiler(filer, sar.getPspId(), ref);
            checkSarFileable(sar, ref);
            return new SarSnapshot(sar.getPspId(), ref, "SAR", List.copyOf(sar.getTransactionReferences()));
        });

        EdgeDataScope scope = scope(snap.pspId(), TunnelActor.Purpose.SAR_FILING, snap.filingRef());
        FilingExtract extract = data.extractSar(scope, snap.kind(), snap.filingRef(), snap.refs());

        return tx.execute(s -> {
            SuspiciousActivityReport sar = sars.lockById(sarId).orElseThrow(() -> new FilingException(
                    FilingException.Code.FILING_NOT_FOUND, "no such SAR", snap.filingRef()));
            checkSarFileable(sar, snap.filingRef());
            if (!sameRefs(sar.getTransactionReferences(), snap.refs())) {
                throw new FilingException(FilingException.Code.FILING_CONCURRENT_CHANGE,
                        "the SAR's transactions changed while its data was pulled; nothing was filed, retry",
                        snap.filingRef());
            }
            Instant filedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            GoAmlFilingRenderer.Filing meta = new GoAmlFilingRenderer.Filing(snap.filingRef(), filedAt, filer, regRef,
                    snap.pspId());
            String content = renderer.renderSar(sar, snap.kind(), extract, meta);
            RegulatoryFiledDocument doc = store.store(new FiledDocumentStore.NewDocument(snap.pspId(),
                    RegulatoryFiledDocument.Kind.valueOf(snap.kind()), RegulatoryFiledDocument.WorkflowType.SAR, sarId,
                    snap.filingRef(), content, extract, regRef, filer, filedAt, retentionYears(snap.pspId())));
            try {
                sarWorkflow.recordFiled(sar, regRef, filer, LocalDateTime.ofInstant(filedAt, ZoneId.systemDefault()));
            } catch (SecurityException | IllegalStateException e) {
                throw new FilingException(FilingException.Code.FILING_STATE_CONFLICT, e.getMessage(), snap.filingRef());
            }
            holdAfterFiling(snap.pspId(), LegalHoldWorkflow.Type.SAR, sarId, "SAR", snap.filingRef(),
                    edgeIds(snap.refs()), filer, filedAt);
            auditDocument(filer, doc, "SAR", sarId, extract);
            log.info("SAR {} filed by user {} as document {} ({} transaction(s), source {})", sarId, filer.getId(),
                    doc.getId(), extract.transactionCount(), extract.source());
            return new FilingResult("SAR", sarId, SarStatus.FILED.name(), FiledDocumentSummary.of(doc));
        });
    }

    /** A SAR can be filed: APPROVED by someone other than its creator and not filed yet. */
    private void checkSarFileable(SuspiciousActivityReport sar, String ref) {
        if (sar.getStatus() == SarStatus.FILED
                || documents.existsByWorkflowTypeAndWorkflowId(RegulatoryFiledDocument.WorkflowType.SAR.name(), sar.getId())) {
            throw new FilingException(FilingException.Code.ALREADY_FILED, "this SAR has already been filed", ref);
        }
        if (sar.getStatus() != SarStatus.APPROVED) {
            throw new FilingException(FilingException.Code.FILING_STATE_CONFLICT,
                    "only an APPROVED SAR can be filed (current status " + sar.getStatus() + ")", ref);
        }
        if (sar.getApprovedBy() == null || sar.getApprovedBy().getId() == null
                || (sar.getCreatedBy() != null && Objects.equals(sar.getCreatedBy().getId(), sar.getApprovedBy().getId()))) {
            throw new FilingException(FilingException.Code.FILING_STATE_CONFLICT,
                    "the SAR has no independent approval (maker-checker)", ref);
        }
    }

    // ── CTR ──────────────────────────────────────────────────────────────────────────────────────

    /** What the check step learned about a CTR deadline. */
    private record CtrSnapshot(long pspId, String filingRef, TransactionReference anchor, Long alertId) {}

    /**
     * Files the CTR of an open CTR_FILING compliance deadline: pulls the anchor transaction and its
     * cash aggregate from the Edge, stores the filed document and completes the deadline.
     *
     * @throws FilingException when the filing is refused (nothing changed)
     */
    public FilingResult fileCtr(long deadlineId, String regulatorReference) {
        User filer = currentUser();
        String regRef = regulatorRef(regulatorReference);
        String ref = "CTR-" + deadlineId;
        CtrSnapshot snap = readOnly.execute(s -> {
            ComplianceDeadline d = deadlines.findById(deadlineId).orElse(null);
            if (d == null || d.getPspId() == null) {
                throw new FilingException(FilingException.Code.FILING_NOT_FOUND, "no such CTR deadline", null);
            }
            requireFiler(filer, d.getPspId(), ref);
            checkCtrFileable(d, ref);
            Long alertId = null;
            TransactionReference anchor;
            if ("ALERT".equals(d.getSourceType()) && d.getSourceId() != null) {
                Alert alert = alerts.findById(d.getSourceId()).orElseThrow(() -> new FilingException(
                        FilingException.Code.FILING_STATE_CONFLICT, "the CTR deadline's alert no longer exists", ref));
                if (alert.getPspId() != null && !alert.getPspId().equals(d.getPspId())) {
                    throw new FilingException(FilingException.Code.FILING_STATE_CONFLICT,
                            "the CTR deadline's alert belongs to another PSP", ref);
                }
                alertId = alert.getAlertId();
                anchor = alert.getTransactionReference();
            } else if ("TRANSACTION".equals(d.getSourceType()) && d.getSourceId() != null) {
                anchor = TransactionReference.ofLegacyTransaction(d.getSourceId());
            } else {
                throw new FilingException(FilingException.Code.FILING_STATE_CONFLICT,
                        "the CTR deadline names no alert or transaction", ref);
            }
            return new CtrSnapshot(d.getPspId(), ref, anchor, alertId);
        });

        EdgeDataScope scope = scope(snap.pspId(), TunnelActor.Purpose.CTR_FILING, snap.filingRef());
        FilingExtract extract = data.extractCtr(scope, snap.filingRef(), snap.anchor());
        FilingExtract.CtrAggregate agg = extract.ctr();
        if (agg.cashTotal().compareTo(agg.threshold()) < 0) {
            throw new FilingException(FilingException.Code.CTR_BELOW_THRESHOLD,
                    "the cash aggregate is below the reporting threshold; no CTR is due", snap.filingRef());
        }

        return tx.execute(s -> {
            ComplianceDeadline d = deadlines.lockById(deadlineId).orElseThrow(() -> new FilingException(
                    FilingException.Code.FILING_NOT_FOUND, "no such CTR deadline", snap.filingRef()));
            checkCtrFileable(d, snap.filingRef());
            Instant filedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            GoAmlFilingRenderer.Filing meta = new GoAmlFilingRenderer.Filing(snap.filingRef(), filedAt, filer, regRef,
                    snap.pspId());
            String content = renderer.renderCtr(d, snap.alertId(), extract, meta);
            RegulatoryFiledDocument doc = store.store(new FiledDocumentStore.NewDocument(snap.pspId(),
                    RegulatoryFiledDocument.Kind.CTR, RegulatoryFiledDocument.WorkflowType.CTR_DEADLINE, deadlineId,
                    snap.filingRef(), content, extract, regRef, filer, filedAt, retentionYears(snap.pspId())));
            Map<String, Object> before = Map.of("deadlineId", deadlineId, "completed", false);
            d.setCompleted(true);
            d.setCompletedAt(LocalDateTime.ofInstant(filedAt, ZoneId.systemDefault()));
            deadlines.save(d);
            List<UUID> held = new ArrayList<>(edgeIds(List.of(snap.anchor())));
            for (String id : agg.edgeAssessmentIds()) {
                uuid(id).ifPresent(u -> {
                    if (!held.contains(u)) {
                        held.add(u);
                    }
                });
            }
            holdAfterFiling(snap.pspId(), LegalHoldWorkflow.Type.CTR_DEADLINE, deadlineId, "CTR", snap.filingRef(),
                    held, filer, filedAt);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("deadlineId", deadlineId);
            after.put("completed", true);
            after.put("filedDocumentId", doc.getId());
            audit.logAction(filer, "FILE_CTR", "COMPLIANCE_DEADLINE", String.valueOf(deadlineId), before, after, null,
                    null);
            auditDocument(filer, doc, "CTR_DEADLINE", deadlineId, extract);
            log.info("CTR deadline {} filed by user {} as document {} (source {})", deadlineId, filer.getId(),
                    doc.getId(), extract.source());
            return new FilingResult("CTR_DEADLINE", deadlineId, "FILED", FiledDocumentSummary.of(doc));
        });
    }

    /** A CTR deadline can be filed: type CTR_FILING, open, nothing filed for it yet. */
    private void checkCtrFileable(ComplianceDeadline d, String ref) {
        if (!"CTR_FILING".equals(d.getDeadlineType())) {
            throw new FilingException(FilingException.Code.FILING_STATE_CONFLICT,
                    "the compliance deadline is not a CTR filing deadline", ref);
        }
        if (documents.existsByWorkflowTypeAndWorkflowId(RegulatoryFiledDocument.WorkflowType.CTR_DEADLINE.name(), d.getId())) {
            throw new FilingException(FilingException.Code.ALREADY_FILED, "this CTR has already been filed", ref);
        }
        if (Boolean.TRUE.equals(d.getCompleted())) {
            throw new FilingException(FilingException.Code.FILING_STATE_CONFLICT,
                    "the CTR deadline was already completed outside the filing workflow", ref);
        }
    }

    // ── shared ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Only the PSP's own MLRO or compliance officer with {@code FILE_SAR} may file. Platform staff
     * never file for a PSP (support access is read-only). A record of another PSP is "not found".
     */
    void requireFiler(User filer, long recordPspId, String ref) {
        if (isolation.isPlatformAdministrator(filer)) {
            throw new FilingException(FilingException.Code.FILING_NOT_ALLOWED,
                    "platform staff cannot file for a PSP; the PSP's MLRO or compliance officer files", ref);
        }
        Long own = filer.getPsp() == null ? null : filer.getPsp().getPspId();
        if (own == null || own != recordPspId) {
            throw new FilingException(FilingException.Code.FILING_NOT_FOUND, "no such record", null);
        }
        String role = filer.getRole() == null || filer.getRole().getName() == null ? ""
                : filer.getRole().getName().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", "");
        if (!FILER_ROLES.contains(role) || !permissions.hasPermission(filer.getRole(), Permission.FILE_SAR)) {
            throw new FilingException(FilingException.Code.FILING_NOT_ALLOWED,
                    "only the PSP's MLRO or compliance officer with the FILE_SAR permission may file", ref);
        }
    }

    /** The filer's pull scope (PSP user of the record's PSP, the filing purpose and reference). */
    private EdgeDataScope scope(long pspId, TunnelActor.Purpose purpose, String filingRef) {
        try {
            return callers.scopeForRecord(callers.current(), pspId, null, purpose,
                    EdgeDataCallerResolver.WorkflowRefs.ofFiling(filingRef));
        } catch (EdgeDataException e) {
            throw FilingException.edgeFailure(e.error(), filingRef);
        }
    }

    /** Covers the filed transactions with a hold released retention years after the filing. */
    private void holdAfterFiling(long pspId, LegalHoldWorkflow.Type type, long workflowId, String reason, String ref,
                                 List<UUID> ids, User filer, Instant filedAt) {
        holds.ensureCoverage(pspId, type, workflowId, reason, ref, ids, filer.getId() == null ? null : "u-" + filer.getId());
        holds.markWorkflowEnded(type, workflowId, filedAt);
    }

    /** The filed-document audit entry (ids, counts and the seal only; never content). */
    private void auditDocument(User filer, RegulatoryFiledDocument doc, String workflowType, long workflowId,
                               FilingExtract extract) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("filedDocumentId", doc.getId());
        after.put("documentKind", doc.getDocumentKind());
        after.put("workflowType", workflowType);
        after.put("workflowId", workflowId);
        after.put("filingRef", doc.getFilingRef());
        after.put("contentSha256", doc.getContentSha256());
        after.put("contentLength", doc.getContentLength());
        after.put("transactionCount", extract.transactionCount());
        after.put("dataSource", extract.source());
        after.put("edgeAuditIds", doc.getEdgeAuditIds());
        after.put("regulatorReference", doc.getRegulatorReference());
        after.put("retainUntil", doc.getRetainUntil().toString());
        audit.logAction(filer, "FILED_DOCUMENT_STORED", "REGULATORY_FILED_DOCUMENT", String.valueOf(doc.getId()),
                null, after, null, null);
    }

    /** The authenticated user. */
    private User currentUser() {
        User u = isolation.getCurrentUser();
        if (u == null) {
            throw new org.springframework.security.access.AccessDeniedException("authentication required");
        }
        return u;
    }

    /** The PSP's retention in years (floor 5). */
    private int retentionYears(long pspId) {
        return Math.max(PspDataSettingsService.MIN_RETENTION_YEARS, dataSettings.retentionState(pspId).years());
    }

    /** The SAR's filing reference as a workflowRef. */
    static String filingRef(SuspiciousActivityReport sar) {
        String r = sar.getSarReference();
        return r != null && WORKFLOW_REF.matcher(r).matches() ? r : "SAR-" + sar.getId();
    }

    /** Validates an optional regulator reference. */
    static String regulatorRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (!REGULATOR_REF.matcher(v).matches()) {
            throw new FilingException(FilingException.Code.FILING_STATE_CONFLICT,
                    "the regulator reference must be 1..128 printable characters", null);
        }
        return v;
    }

    /** Same references in the same order. */
    private static boolean sameRefs(List<TransactionReference> a, List<TransactionReference> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Decision record ids of references. */
    private static List<UUID> edgeIds(List<TransactionReference> refs) {
        return refs.stream().map(TransactionReference::edgeAssessmentId).filter(Objects::nonNull).distinct().toList();
    }

    /** A UUID, when the text is one. */
    private static java.util.Optional<UUID> uuid(String text) {
        try {
            return java.util.Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }
}
