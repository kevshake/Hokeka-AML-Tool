package com.posgateway.aml.service.edgedata;

import com.posgateway.aml.dto.alert.TransactionAlertsResponseDTO;
import com.posgateway.aml.dto.assessment.AssessmentDetailResponseDTO;
import com.posgateway.aml.dto.compliance.ComplianceLinksResponse;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.alert.TransactionAlertQueryService;
import com.posgateway.aml.service.assessment.AssessmentQueryService;
import com.posgateway.aml.service.compliance.ComplianceLinksQueryService;
import org.springframework.stereotype.Service;

/**
 * The cloud-held parts of a transaction view, read from the cloud database by ID only (R6):
 * {@code /transactions/{id}/alerts}, {@code /{id}/assessment} and {@code /{id}/compliance-links}.
 * They no longer read the cloud {@code transactions} table except, for a legacy numeric id, to check
 * that the row belongs to the caller's PSP.
 *
 * <p>These are workflow records, not transaction data, so platform administrators read them as
 * every other cloud workflow view lets them (no support access needed); PSP users read only their
 * own PSP's, and a {@code pspId} parameter naming another PSP is {@code 403 TENANT_MISMATCH}.
 */
@Service
public class TransactionLinksService {

    private final EdgeDataCallerResolver callers;
    private final TransactionSourceResolver sources;
    private final TransactionAlertQueryService alerts;
    private final AssessmentQueryService assessments;
    private final ComplianceLinksQueryService links;
    private final TransactionRepository transactions;

    /** Wires the links reader. */
    public TransactionLinksService(EdgeDataCallerResolver callers, TransactionSourceResolver sources,
                                   TransactionAlertQueryService alerts, AssessmentQueryService assessments,
                                   ComplianceLinksQueryService links, TransactionRepository transactions) {
        this.callers = callers;
        this.sources = sources;
        this.alerts = alerts;
        this.assessments = assessments;
        this.links = links;
        this.transactions = transactions;
    }

    /** The compliance links of a transaction plus where the transaction lives. */
    public record SourcedLinks(ComplianceLinksResponse links, String source) {}

    /** Alerts on a transaction (decision record id, or a legacy id). */
    public TransactionAlertsResponseDTO alerts(String rawId, Long requestedPspId) {
        TransactionId id = TransactionId.parse(rawId, "/id");
        Long psp = tenant(requestedPspId);
        TransactionAlertsResponseDTO dto;
        if (id.isEdge()) {
            dto = alerts.getAlertsForDecisionRecord(psp, id.edgeAssessmentId());
            dto.setSource(sourceOf(psp, TransactionSource.EDGE));
        } else {
            checkLegacyOwner(psp, id.legacyId());
            dto = alerts.getAlertsForTransaction(id.legacyId());
            dto.setSource(TransactionSource.CLOUD_LEGACY.name());
        }
        return dto;
    }

    /** The decision record / assessment of a transaction with its findings. */
    public AssessmentDetailResponseDTO assessment(String rawId) {
        TransactionId id = TransactionId.parse(rawId, "/id");
        return id.isEdge() ? assessments.getByReferenceId(id.edgeAssessmentId())
                : assessments.getLatestByTransactionId(id.legacyId());
    }

    /** Alerts, cases, SARs, disputes and screening runs linked to a transaction. */
    public SourcedLinks complianceLinks(String rawId, Long requestedPspId) {
        TransactionId id = TransactionId.parse(rawId, "/id");
        Long psp = tenant(requestedPspId);
        if (id.isEdge()) {
            return new SourcedLinks(links.forDecisionRecord(psp, id.edgeAssessmentId()),
                    sourceOf(psp, TransactionSource.EDGE));
        }
        checkLegacyOwner(psp, id.legacyId());
        return new SourcedLinks(links.forTransaction(id.legacyId()), TransactionSource.CLOUD_LEGACY.name());
    }

    /**
     * The PSP to scope by: the caller's own for PSP users (a different {@code pspId} is refused),
     * the optional parameter for platform administrators ({@code null} = every PSP).
     */
    private Long tenant(Long requestedPspId) {
        EdgeDataCaller caller = callers.current();
        if (caller.pspUser()) {
            if (requestedPspId != null && !requestedPspId.equals(caller.pspId())) {
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.TENANT_MISMATCH,
                        "a PSP user can only read its own PSP"));
            }
            return caller.pspId();
        }
        return requestedPspId;
    }

    /** A legacy row must belong to the PSP in scope (404 otherwise, without revealing it exists). */
    private void checkLegacyOwner(Long pspId, Long txnId) {
        boolean visible = pspId == null ? transactions.existsById(txnId) : transactions.existsByTxnIdAndPspId(txnId, pspId);
        if (!visible) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.NOT_FOUND, "no such transaction"));
        }
    }

    /** The PSP's source when known; the platform-wide view of a decision record is the Edge. */
    private String sourceOf(Long pspId, TransactionSource fallback) {
        return pspId == null ? fallback.name() : sources.sourceFor(pspId).name();
    }
}
