package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.dto.common.KeysetPageResponse;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.edge.tunnel.TunnelOp;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.service.pagination.KeysetSliceService;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Reads of legacy cloud-ingest rows for PSPs that have no Edge ({@link TransactionSource#CLOUD_LEGACY}),
 * until R12 deletes the table. Every read is held to the same rules as an Edge pull: the PSP comes
 * from the scope, a platform read needs the §9 support-access gate, and each read writes one
 * {@code edge_data_access_log} row (op named after the Edge op it stands in for, no sites) so the
 * PSP's access log shows it.
 *
 * <p>Methods are deliberately not transactional: the audit row is written and completed in its own
 * transactions (a read-only outer transaction would refuse or drop them).
 */
@Service
public class LegacyTransactionReadService {

    private static final Logger log = LoggerFactory.getLogger(LegacyTransactionReadService.class);
    private static final int EXPORT_PAGE = 500;

    private final TransactionRepository transactions;
    private final AssessmentRepository assessments;
    private final KeysetSliceService keyset;
    private final TransactionReferenceResolver references;
    private final EdgeTunnelAccess tunnel;

    /** Wires the legacy reader. */
    public LegacyTransactionReadService(TransactionRepository transactions, AssessmentRepository assessments,
                                        KeysetSliceService keyset, TransactionReferenceResolver references,
                                        EdgeTunnelAccess tunnel) {
        this.transactions = transactions;
        this.assessments = assessments;
        this.keyset = keyset;
        this.references = references;
        this.tunnel = tunnel;
    }

    /** One page of the PSP's legacy rows (newest first, keyset cursor), as the §7.14 envelope. */
    public ObjectNode list(EdgeDataScope scope, TransactionFilter filter, int limit, String cursor) {
        return audited(scope, TunnelOp.TXN_SEARCH, filter.toJson(), () -> {
            KeysetPageResponse<TransactionEntity> page = keyset.slice(transactions, spec(scope.pspId(), filter), 0,
                    limit, cursor, "txnTs", "txnId", TransactionEntity::getTxnTs, TransactionEntity::getTxnId);
            List<ObjectNode> items = page.content().stream().map(t -> EdgeConsoleView.legacyItem(t, null)).toList();
            return new Audited<>(EdgeConsoleView.legacyEnvelope(items, page.nextCursor(), List.of(), false), items.size());
        });
    }

    /**
     * One legacy row by its cloud id, or by the reference id R8 gives it (the cloud assessment id).
     *
     * @throws EdgeDataException {@code 404 NOT_FOUND} when the PSP has no such row
     */
    public ObjectNode get(EdgeDataScope scope, Long txnId, UUID referenceId) {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.put("edgeAssessmentId", referenceId != null ? referenceId.toString() : legacyRef(txnId));
        return audited(scope, TunnelOp.TXN_GET, params, () -> {
            Long id = txnId != null ? txnId : legacyIdOf(scope.pspId(), referenceId).orElse(null);
            TransactionEntity t = id == null ? null : transactions.findByTxnIdAndPspId(id, scope.pspId()).orElse(null);
            if (t == null) {
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.NOT_FOUND, "no such transaction"));
            }
            return new Audited<>(EdgeConsoleView.legacyItem(t, references.forLegacyTransaction(t)), 1);
        });
    }

    /**
     * Legacy rows named by workflow references (legacy id, else the reference id), in reference
     * order, as the batchGet envelope; references that resolve to no row are {@code missing}.
     */
    public ObjectNode batchGet(EdgeDataScope scope, Collection<TransactionReference> refs) {
        List<TransactionReference> list = refs.stream().filter(r -> r != null && !r.isEmpty()).toList();
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        ArrayNode ids = params.putArray("edgeAssessmentIds");
        list.forEach(r -> ids.add(r.edgeAssessmentId() != null ? r.edgeAssessmentId().toString()
                : legacyRef(r.legacyTransactionId())));
        return audited(scope, TunnelOp.TXN_BATCH_GET, params, () -> {
            Map<TransactionReference, Long> idByRef = new LinkedHashMap<>();
            for (TransactionReference r : list) {
                Long id = r.legacyTransactionId() != null ? r.legacyTransactionId()
                        : r.edgeAssessmentId() == null ? null : legacyIdOf(scope.pspId(), r.edgeAssessmentId()).orElse(null);
                idByRef.put(r, id);
            }
            Set<Long> wanted = new LinkedHashSet<>();
            idByRef.values().stream().filter(java.util.Objects::nonNull).forEach(wanted::add);
            Map<Long, TransactionEntity> rows = new LinkedHashMap<>();
            if (!wanted.isEmpty()) {
                transactions.findByPspIdAndTxnIdIn(scope.pspId(), wanted).forEach(t -> rows.put(t.getTxnId(), t));
            }
            List<ObjectNode> items = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            Set<Long> emitted = new TreeSet<>();
            idByRef.forEach((ref, id) -> {
                TransactionEntity t = id == null ? null : rows.get(id);
                if (t == null) {
                    missing.add(ref.edgeAssessmentId() != null ? ref.edgeAssessmentId().toString()
                            : legacyRef(ref.legacyTransactionId()));
                } else if (emitted.add(id)) {
                    items.add(EdgeConsoleView.legacyItem(t, ref));
                }
            });
            return new Audited<>(EdgeConsoleView.legacyEnvelope(items, null, missing, true), items.size());
        });
    }

    /**
     * Streams the PSP's legacy rows matching {@code filter} (newest first) to {@code sink}, at most
     * {@code maxRows}, page by page: nothing is buffered beyond one page, nothing is written anywhere.
     *
     * @return rows streamed
     */
    public long export(EdgeDataScope scope, TransactionFilter filter, long maxRows, Consumer<ObjectNode> sink) {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.set("filter", filter.toJson());
        params.put("maxRows", maxRows);
        return audited(scope, TunnelOp.EXPORT_STREAM, params, () -> {
            Specification<TransactionEntity> spec = spec(scope.pspId(), filter);
            Sort sort = Sort.by(Sort.Direction.DESC, "txnTs", "txnId");
            long written = 0;
            int page = 0;
            while (written < maxRows) {
                List<TransactionEntity> rows = transactions.findAll(spec, PageRequest.of(page++, EXPORT_PAGE, sort))
                        .getContent();
                if (rows.isEmpty()) {
                    break;
                }
                for (TransactionEntity t : rows) {
                    if (written >= maxRows) {
                        break;
                    }
                    sink.accept(EdgeConsoleView.legacyItem(t, null));
                    written++;
                }
            }
            return new Audited<>(written, (int) Math.min(Integer.MAX_VALUE, written));
        });
    }

    /** Result of an audited read with its item count. */
    private record Audited<T>(T value, int items) {}

    /** A read body that may throw an {@link EdgeDataException}. */
    @FunctionalInterface
    private interface Read<T> {
        Audited<T> run();
    }

    /**
     * Writes the access-log row, applies the §9 gate to platform reads, runs the read and completes
     * the row with the outcome (also when the read is refused or fails).
     */
    private <T> T audited(EdgeDataScope scope, TunnelOp op, ObjectNode params, Read<T> read) {
        UUID auditId = UUID.randomUUID();
        try {
            tunnel.auditor().begin(auditId, scope.actor(), op, params);
        } catch (RuntimeException e) {
            log.error("Legacy transaction read refused: access audit row could not be written ({})",
                    e.getClass().getSimpleName());
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.INTERNAL, "access audit unavailable"));
        }
        String outcome = TunnelErrorCode.INTERNAL.name();
        int items = 0;
        try {
            gate(scope);
            Audited<T> result = read.run();
            items = result.items();
            outcome = "OK";
            return result.value();
        } catch (EdgeDataException e) {
            outcome = e.error().code().name();
            throw e;
        } catch (java.io.UncheckedIOException e) {
            // The client went away mid-export: recorded as cancelled, rethrown for the container.
            outcome = TunnelErrorCode.CANCELLED.name();
            throw e;
        } catch (RuntimeException e) {
            log.error("Legacy transaction read {} failed: {}", op.wire(), e.getClass().getSimpleName());
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.INTERNAL, "internal error"));
        } finally {
            try {
                tunnel.auditor().complete(auditId, Set.of(), Set.of(), items, 0, outcome);
            } catch (RuntimeException e) {
                log.error("Legacy read access audit {} could not be completed ({})", auditId,
                        e.getClass().getSimpleName());
            }
        }
    }

    /** §9 for platform reads: support access on, purpose SUPPORT_ACCESS, 10..500 char reason, platform role. */
    private void gate(EdgeDataScope scope) {
        TunnelActor a = scope.actor();
        if (a.kind() != TunnelActor.Kind.PLATFORM_USER) {
            return;
        }
        String reason = a.supportReason();
        boolean ok = a.purpose() == TunnelActor.Purpose.SUPPORT_ACCESS && reason != null && reason.length() >= 10
                && reason.length() <= 500 && TunnelActor.PLATFORM_ROLES.contains(a.role())
                && tunnel.supportAccess().isSupportAccessEnabled(scope.pspId());
        if (!ok) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.SUPPORT_ACCESS_DISABLED,
                    "support access is not enabled for this PSP or the pull lacks a reason"));
        }
    }

    /** The legacy row id behind a reference id: the PSP's TXN assessment with that id. */
    private Optional<Long> legacyIdOf(long pspId, UUID referenceId) {
        if (referenceId == null) {
            return Optional.empty();
        }
        for (Assessment a : assessments.findReferencedByPsp(pspId, Set.of(referenceId))) {
            if (a.getTxnId() != null) {
                return Optional.of(a.getTxnId());
            }
        }
        return Optional.empty();
    }

    /**
     * Audit stand-in for a legacy id in an id list: the §11 back-fill id (UUIDv5 over
     * {@code <pspId>:<legacyId>}) is not known here, so a fixed UUID-shaped token carrying no data.
     */
    private static String legacyRef(Long txnId) {
        return new UUID(0L, txnId == null ? 0L : txnId).toString();
    }

    /** The PSP-scoped legacy filter: PSP, time window and the searchFilter fields the table has. */
    private static Specification<TransactionEntity> spec(long pspId, TransactionFilter f) {
        // The legacy table has no rule-id, score or sanctions columns: refuse rather than ignore.
        if (f.ruleId() != null) {
            throw new EdgeDataException(TunnelError.invalidParams("/ruleId"));
        }
        if (f.minScore() != null) {
            throw new EdgeDataException(TunnelError.invalidParams("/minScore"));
        }
        if (f.sanctionsHit() != null) {
            throw new EdgeDataException(TunnelError.invalidParams("/sanctionsHit"));
        }
        LocalDateTime from = LocalDateTime.ofInstant(f.from(), ZoneOffset.UTC);
        LocalDateTime to = LocalDateTime.ofInstant(f.to(), ZoneOffset.UTC);
        Specification<TransactionEntity> s = (root, q, cb) -> cb.and(cb.equal(root.get("pspId"), pspId),
                cb.isNotNull(root.get("txnTs")), cb.greaterThanOrEqualTo(root.get("txnTs"), from),
                cb.lessThan(root.get("txnTs"), to));
        if (f.merchantId() != null) {
            s = s.and((root, q, cb) -> cb.equal(root.get("merchantId"), f.merchantId()));
        }
        if (f.decisions() != null) {
            s = s.and((root, q, cb) -> root.get("decision").in(f.decisions()));
        }
        if (f.txnRef() != null) {
            s = s.and((root, q, cb) -> cb.equal(root.get("clientReference"), f.txnRef()));
        }
        if (f.ctrRequired() != null) {
            s = s.and((root, q, cb) -> cb.equal(root.get("ctrRequired"), f.ctrRequired()));
        }
        return s;
    }
}
