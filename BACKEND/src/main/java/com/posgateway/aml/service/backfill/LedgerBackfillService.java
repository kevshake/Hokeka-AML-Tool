package com.posgateway.aml.service.backfill;

import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.residency.PspEdgeSettings;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.repository.residency.PspEdgeSettingsRepository;
import com.posgateway.aml.service.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Operator control of the legacy back-fill (plan R15, contract §11): start, pause, resume, abort,
 * resolve rejected rows, and the per-PSP report. Every action is checked by
 * {@link LedgerBackfillAccess} and audited through {@link AuditLogService} with the caller's reason;
 * every Edge request the run makes is audited in {@code edge_data_access_log} by the gateway.
 *
 * <p><b>Target site (§11 step 1, R15 routing rule).</b> A single-site PSP's rows go to its only
 * site. A multi-site PSP must have a default target site chosen by the platform operator. A
 * merchant whose Edge decision records all name one current site of the PSP is routed to that site
 * (frozen at the first start); a merchant with none, or with records at two or more sites, is
 * ambiguous and goes to the default site, as do multi-asset rows (no merchant). A start creates one
 * run per site that receives rows.
 *
 * <p>Nothing here deletes cloud data (that is R12, gated by {@link BackfillCoverageService}).
 */
@Service
public class LedgerBackfillService {

    private final BackfillRunStore store;
    private final LegacyRowReader reader;
    private final BackfillCoverageService coverage;
    private final LedgerBackfillAccess access;
    private final PspRepository psps;
    private final PspEdgeSettingsRepository settings;
    private final EdgeNodeRepository nodes;
    private final AuditLogService audit;
    private final BackfillProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    /** Wires the service. */
    @org.springframework.beans.factory.annotation.Autowired
    public LedgerBackfillService(BackfillRunStore store, LegacyRowReader reader, BackfillCoverageService coverage,
                                 LedgerBackfillAccess access, PspRepository psps, PspEdgeSettingsRepository settings,
                                 EdgeNodeRepository nodes, AuditLogService audit, BackfillProperties props,
                                 PlatformTransactionManager txManager) {
        this(store, reader, coverage, access, psps, settings, nodes, audit, props, txManager, Clock.systemUTC());
    }

    /** Explicit clock (tests). */
    LedgerBackfillService(BackfillRunStore store, LegacyRowReader reader, BackfillCoverageService coverage,
                          LedgerBackfillAccess access, PspRepository psps, PspEdgeSettingsRepository settings,
                          EdgeNodeRepository nodes, AuditLogService audit, BackfillProperties props,
                          PlatformTransactionManager txManager, Clock clock) {
        this.store = store;
        this.reader = reader;
        this.coverage = coverage;
        this.access = access;
        this.psps = psps;
        this.settings = settings;
        this.nodes = nodes;
        this.audit = audit;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    /** Body of a start: the default target site (required for a multi-site PSP) and the reason. */
    public record StartRequest(String defaultSiteId, String reason) {}

    /**
     * Starts a back-fill of the PSP: one PENDING run per receiving site, each with its window
     * [{@code now − retention}, {@code now}) frozen and its expected row count computed.
     *
     * @throws ResponseStatusException 404 unknown PSP; 409 preconditions not met or a run unfinished
     */
    public List<BackfillRun> start(long pspId, StartRequest request) {
        String reason = request == null ? null : request.reason();
        LedgerBackfillAccess.Caller caller = access.requirePlatformOperator(pspId, reason);
        Psp psp = psps.findById(pspId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PSP not found"));
        requireConsent(pspId);
        if (store.hasUnfinishedRun(pspId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A back-fill of this PSP is still unfinished");
        }
        List<String> sites = activeSites(pspId);
        if (sites.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The PSP has no active Edge site");
        }
        String defaultSite = chooseDefault(pspId, request.defaultSiteId(), sites);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        int years = psp.getRetentionYears() == null ? 5 : Math.max(5, psp.getRetentionYears());
        Instant cutoff = now.atOffset(ZoneOffset.UTC).minusYears(years).toInstant();
        String why = reason.strip();

        List<UUID> created = tx.execute(status -> {
            String previous = store.target(pspId).orElse(null);
            if (!defaultSite.equals(previous)) {
                store.saveTarget(pspId, defaultSite, caller.name(), why);
                audit.logAction(caller.user(), "EDGE_BACKFILL_TARGET_SET", "PSP", String.valueOf(pspId),
                        previous == null ? null : Map.of("defaultSiteId", previous), Map.of("defaultSiteId", defaultSite),
                        null, why);
            }
            // Merchant routes are frozen at the PSP's first back-fill, so top-up runs never split a merchant.
            if (sites.size() > 1 && !store.merchantSitesFrozen(pspId)) {
                Map<String, String> routes = new LinkedHashMap<>(store.unambiguousMerchantSites(pspId, sites));
                routes.values().removeIf(defaultSite::equals);
                store.freezeMerchantSites(pspId, routes);
            }
            Set<String> runSites = new LinkedHashSet<>();
            runSites.add(defaultSite);
            store.routedSites(pspId).stream().filter(sites::contains).forEach(runSites::add);
            List<UUID> ids = new ArrayList<>();
            for (String site : runSites) {
                UUID id = UUID.randomUUID();
                store.insertRun(id, pspId, site, site.equals(defaultSite), sites, cutoff, now, years,
                        props.getBatchBytes(), caller.name(), why);
                store.setExpectedRows(id, reader.count(id, cutoff, now));
                ids.add(id);
            }
            return ids;
        });
        List<BackfillRun> runs = new ArrayList<>();
        for (UUID id : created == null ? List.<UUID>of() : created) {
            BackfillRun run = store.find(pspId, id).orElseThrow();
            runs.add(run);
            Map<String, Object> after = BackfillEngine.summary(run);
            after.put("sourceFrom", run.sourceFrom().toString());
            after.put("sourceTo", run.sourceTo().toString());
            after.put("retentionYears", run.retentionYears());
            audit.logAction(caller.user(), "EDGE_BACKFILL_STARTED", "EDGE_BACKFILL_RUN", id.toString(), null, after,
                    null, why);
        }
        return runs;
    }

    /** Pauses a run (the worker stops after the batch in flight). */
    public BackfillRun pause(long pspId, UUID backfillId, String reason) {
        LedgerBackfillAccess.Caller caller = access.requireOperatorOrPsp(pspId, reason);
        return change(pspId, backfillId, BackfillState.PAUSABLE, BackfillState.PAUSED, null, caller,
                "EDGE_BACKFILL_PAUSED", reason);
    }

    /** Resumes a paused, stalled or failed run from its persisted step (preconditions re-checked). */
    public BackfillRun resume(long pspId, UUID backfillId, String reason) {
        LedgerBackfillAccess.Caller caller = access.requirePlatformOperator(pspId, reason);
        requireConsent(pspId);
        return change(pspId, backfillId, BackfillState.RESUMABLE, BackfillState.RUNNING, null, caller,
                "EDGE_BACKFILL_RESUMED", reason);
    }

    /** Aborts a run: ABORT is sent to the Edge if it began; rows already on the Edge stay. */
    public BackfillRun abort(long pspId, UUID backfillId, String reason) {
        LedgerBackfillAccess.Caller caller = access.requireOperatorOrPsp(pspId, reason);
        return change(pspId, backfillId, BackfillState.ABORTABLE, BackfillState.ABORTING, "OPERATOR", caller,
                "EDGE_BACKFILL_ABORT_REQUESTED", reason);
    }

    /**
     * Operator decision on rejected rows: {@code EXCLUDED} means the row is not copied and R12 may
     * purge it. Only unresolved rejected rows of this run change.
     *
     * @return rows resolved
     */
    public int exclude(long pspId, UUID backfillId, Collection<String> legacyTransactionIds, String reason) {
        LedgerBackfillAccess.Caller caller = access.requireOperatorOrPsp(pspId, reason);
        BackfillRun run = store.find(pspId, backfillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Back-fill run not found"));
        if (legacyTransactionIds == null || legacyTransactionIds.isEmpty() || legacyTransactionIds.size() > 1_000) {
            throw new IllegalArgumentException("name 1 to 1000 legacyTransactionIds");
        }
        Set<String> ids = new TreeSet<>(legacyTransactionIds);
        int n = store.resolve(run.backfillId(), ids, "EXCLUDED", reason.strip(), caller.name());
        audit.logAction(caller.user(), "EDGE_BACKFILL_ROWS_EXCLUDED", "EDGE_BACKFILL_RUN", backfillId.toString(), null,
                Map.of("pspId", pspId, "requested", ids.size(), "excluded", n), null, reason.strip());
        return n;
    }

    /** Per-PSP report: every run with its counts, and the R12 purge readiness. */
    public PspReport report(long pspId, String reason) {
        LedgerBackfillAccess.Caller caller = access.requireReader(pspId, reason);
        if (!psps.existsById(pspId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PSP not found");
        }
        List<RunReport> runs = store.runs(pspId).stream().map(this::runReport).toList();
        if (caller.platform()) {
            audit.logAction(caller.user(), "EDGE_BACKFILL_REPORT_VIEWED", "PSP", String.valueOf(pspId), null, null, null,
                    reason.strip());
        }
        return new PspReport(pspId, store.target(pspId).orElse(null), runs, coverage.purgeReadiness(pspId));
    }

    /** Rejected rows of a run (legacy ids and codes only), paged. */
    public List<BackfillRunStore.RowRecord> rejections(long pspId, UUID backfillId, int page, int size, String reason) {
        LedgerBackfillAccess.Caller caller = access.requireReader(pspId, reason);
        BackfillRun run = store.find(pspId, backfillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Back-fill run not found"));
        int limit = Math.max(1, Math.min(500, size));
        if (caller.platform()) {
            audit.logAction(caller.user(), "EDGE_BACKFILL_REJECTIONS_VIEWED", "EDGE_BACKFILL_RUN", backfillId.toString(),
                    null, null, null, reason.strip());
        }
        return store.rejected(run.backfillId(), Math.max(0, page) * limit, limit);
    }

    // ── report types ─────────────────────────────────────────────────────────────────────────────

    /** The report of one PSP. */
    public record PspReport(long pspId, String defaultSiteId, List<RunReport> runs,
                            BackfillCoverageService.PurgeReadiness purge) {}

    /**
     * One run: rows sent (recorded), accepted, duplicates (as acknowledged; re-sends after an
     * interrupted acknowledgement count as duplicates), held, rejected by code, resolved by kind,
     * pending operator decision, and verified (held rows of a VERIFIED run).
     */
    public record RunReport(UUID backfillId, String siteId, boolean defaultSite, String state, String phase,
                            Instant sourceFrom, Instant sourceTo, int retentionYears, long expectedRows, long rowsSent,
                            long accepted, long duplicates, long held, long rejected, Map<String, Long> rejectedByCode,
                            Map<String, Long> resolvedBy, long pendingDecision, long verified, Long edgeRowsTotal,
                            String expectedIdDigest, String edgeIdDigest, Integer sampleSize, Integer sampleMismatches,
                            int resendRound, String lastErrorCode, Instant lastErrorAt, int consecutiveFailures,
                            Instant nextAttemptAt, String createdBy, String createdReason, Instant createdAt,
                            Instant begunAt, Instant committedAt, Instant verifiedAt, Instant finishedAt) {}

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** A conditional state change of a run of the PSP, audited. */
    private BackfillRun change(long pspId, UUID backfillId, Set<BackfillState> from, BackfillState to, String abortReason,
                               LedgerBackfillAccess.Caller caller, String action, String reason) {
        BackfillRun before = store.find(pspId, backfillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Back-fill run not found"));
        if (!store.transition(pspId, backfillId, from, to, abortReason)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The run is " + before.state() + "; cannot move to " + to);
        }
        BackfillRun after = store.find(pspId, backfillId).orElseThrow();
        audit.logAction(caller.user(), action, "EDGE_BACKFILL_RUN", backfillId.toString(),
                Map.of("pspId", pspId, "state", before.state().name()), Map.of("pspId", pspId, "state", after.state().name()),
                null, reason.strip());
        return after;
    }

    /** §11 step 1: the PSP's config allows the back-fill (cloud side of the consent). */
    private void requireConsent(long pspId) {
        PspEdgeSettings s = settings.findById(pspId).orElseGet(() -> PspEdgeSettings.defaultsFor(pspId));
        if (!s.isTunnelEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The PSP's Edge Tunnel is disabled");
        }
        if (!s.isTunnelBackfillAllowed() || !s.getTunnelOpsAllowed().contains("ledger.backfill")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The PSP's Edge config does not allow ledger.backfill (tunnel.backfillAllowed and opsAllowed)");
        }
    }

    /** The PSP's active sites, sorted. */
    private List<String> activeSites(long pspId) {
        Set<String> out = new TreeSet<>();
        for (EdgeNode n : nodes.findByPspIdAndStatus(pspId, EdgeNodeStatus.ACTIVE)) {
            out.add(n.effectiveSiteId());
        }
        return List.copyOf(out);
    }

    /** The default target: requested, else recorded, else the only site; a multi-site PSP must choose. */
    private String chooseDefault(long pspId, String requested, List<String> sites) {
        String pick = requested != null && !requested.isBlank() ? requested.strip() : store.target(pspId).orElse(null);
        if (pick == null && sites.size() == 1) {
            pick = sites.get(0);
        }
        if (pick == null) {
            throw new IllegalArgumentException("defaultSiteId is required: the PSP has " + sites.size() + " Edge sites");
        }
        if (!sites.contains(pick)) {
            throw new IllegalArgumentException("defaultSiteId is not an active Edge site of this PSP");
        }
        return pick;
    }

    /** Builds a run's report from its row and its row counts. */
    private RunReport runReport(BackfillRun r) {
        Map<String, Long> counts = store.rowCounts(r.backfillId());
        long held = 0;
        long rejected = 0;
        long pending = 0;
        Map<String, Long> byCode = new LinkedHashMap<>();
        Map<String, Long> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            String[] k = e.getKey().split("\\|", -1);
            if ("HELD".equals(k[0])) {
                held += e.getValue();
            } else {
                rejected += e.getValue();
                byCode.merge(k[1], e.getValue(), Long::sum);
                if (k[2].isEmpty()) {
                    pending += e.getValue();
                } else {
                    resolved.merge(k[2], e.getValue(), Long::sum);
                }
            }
        }
        long verified = r.state() == BackfillState.VERIFIED ? held : 0;
        return new RunReport(r.backfillId(), r.siteId(), r.defaultSite(), r.state().name(), r.phase().name(),
                r.sourceFrom(), r.sourceTo(), r.retentionYears(), r.expectedRows(), held + rejected, r.rowsAccepted(),
                r.rowsDuplicate(), held, rejected, byCode, resolved, pending, verified, r.edgeRowsTotal(),
                r.expectedIdDigest(), r.edgeIdDigest(), r.sampleSize(), r.sampleMismatches(), r.resendRound(),
                r.lastErrorCode(), r.lastErrorAt(), r.consecutiveFailures(), r.nextAttemptAt(), r.createdBy(),
                r.createdReason(), r.createdAt(), r.begunAt(), r.committedAt(), r.verifiedAt(), r.finishedAt());
    }
}
