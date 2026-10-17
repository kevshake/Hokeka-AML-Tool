package com.posgateway.aml.service.legalhold;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.Jcs;
import com.posgateway.aml.edge.tunnel.TunnelNotifier;
import com.posgateway.aml.edge.tunnel.TunnelRegistry;
import com.posgateway.aml.edge.tunnel.TunnelSessionListener;
import com.posgateway.aml.edge.tunnel.TunnelTime;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.filing.LegalHold;
import com.posgateway.aml.entity.filing.LegalHoldSiteAck;
import com.posgateway.aml.entity.filing.LegalHoldWorkflow;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.repository.filing.LegalHoldRepository;
import com.posgateway.aml.repository.filing.LegalHoldSiteAckRepository;
import com.posgateway.aml.repository.filing.LegalHoldWorkflowRepository;
import com.posgateway.aml.service.residency.PspDataSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The cloud side of legal holds (work package R9; residency contract §7.10, §8.3, §8.4). The cloud
 * is the holds' source of truth: this service places holds for workflow records (a drafted SAR, an
 * escalated case, a filed CTR), records them with their per-site delivery state, sends
 * {@code notify hold.place} / {@code hold.release} to every Edge Site of the PSP after the
 * placing transaction commits, records {@code hold.ack}s, re-sends every unconfirmed hold after a
 * site's {@code hello.ack}, and releases holds once their workflow has ended and the PSP's
 * retention has elapsed.
 *
 * <p>Holds carry decision record ids only (class D); no transaction data is read or stored here.
 */
@Service
public class LegalHoldService {

    private static final Logger log = LoggerFactory.getLogger(LegalHoldService.class);

    /** {@code common.schema.json#/$defs/workflowRef}. */
    private static final Pattern WORKFLOW_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");

    /** {@code common.schema.json#/$defs/userRef}. */
    private static final Pattern USER_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._@:-]{0,127}$");

    /** Releasing actor of retention-driven releases. */
    static final String RETENTION_JOB = "system:legal-hold-retention";

    /** A digest mismatch re-sends rows not sent within this window (in-flight sends are left alone). */
    static final Duration RESEND_AFTER = Duration.ofSeconds(30);

    /** Result of {@link #ensureCoverage}. */
    public record Coverage(List<UUID> placedHoldIds, int newlyCovered, int alreadyCovered) {}

    private final LegalHoldWorkflowRepository workflows;
    private final LegalHoldRepository holds;
    private final LegalHoldSiteAckRepository siteAcks;
    private final EdgeNodeRepository nodes;
    private final PspDataSettingsService dataSettings;
    private final ObjectProvider<TunnelNotifier> notifier;
    private final ObjectProvider<TunnelRegistry> registry;
    private final TransactionTemplate required;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    /** Wires the hold service; the tunnel beans are optional (holds stay pending without them). */
    @org.springframework.beans.factory.annotation.Autowired
    public LegalHoldService(LegalHoldWorkflowRepository workflows, LegalHoldRepository holds,
                            LegalHoldSiteAckRepository siteAcks, EdgeNodeRepository nodes,
                            PspDataSettingsService dataSettings, ObjectProvider<TunnelNotifier> notifier,
                            ObjectProvider<TunnelRegistry> registry, PlatformTransactionManager transactionManager) {
        this(workflows, holds, siteAcks, nodes, dataSettings, notifier, registry, transactionManager,
                Clock.systemUTC());
    }

    /** Test constructor with an explicit clock. */
    public LegalHoldService(LegalHoldWorkflowRepository workflows, LegalHoldRepository holds,
                            LegalHoldSiteAckRepository siteAcks, EdgeNodeRepository nodes,
                            PspDataSettingsService dataSettings, ObjectProvider<TunnelNotifier> notifier,
                            ObjectProvider<TunnelRegistry> registry, PlatformTransactionManager transactionManager,
                            Clock clock) {
        this.workflows = workflows;
        this.holds = holds;
        this.siteAcks = siteAcks;
        this.nodes = nodes;
        this.dataSettings = dataSettings;
        this.notifier = notifier;
        this.registry = registry;
        this.required = new TransactionTemplate(transactionManager);
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    // ── placing ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Makes sure every id in {@code ids} is covered by an ACTIVE hold of the workflow record,
     * placing new holds (≤ 1000 ids each) for the ids not yet covered. Joins the caller's
     * transaction; the notifies go out after it commits.
     *
     * @param reasonCode §7.10 reasonCode (CASE/SAR/STR/CTR/REGULATOR_REQUEST/LITIGATION)
     * @param reference  workflowRef sent to the Edge (e.g. the SAR reference)
     * @param placedBy   userRef of the actor ({@code u-<id>} or {@code system:<job>})
     */
    public Coverage ensureCoverage(long pspId, LegalHoldWorkflow.Type type, long workflowId, String reasonCode,
                                   String reference, Collection<UUID> ids, String placedBy) {
        LinkedHashSet<UUID> wanted = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(Objects::nonNull).forEach(wanted::add);
        }
        if (wanted.isEmpty()) {
            return new Coverage(List.of(), 0, 0);
        }
        String ref = safeRef(reference, type, workflowId);
        String by = safeUser(placedBy);
        Coverage result = required.execute(status -> {
            LegalHoldWorkflow wf = lockOrCreate(pspId, type, workflowId, reasonCode, ref);
            Set<UUID> covered = new LinkedHashSet<>();
            for (LegalHold h : holds.findByWorkflowRefId(wf.getId())) {
                if (h.isActive()) {
                    covered.addAll(h.getEdgeAssessmentIds());
                }
            }
            List<UUID> missing = wanted.stream().filter(id -> !covered.contains(id)).toList();
            List<UUID> placed = new ArrayList<>();
            Instant now = clock.instant();
            Set<String> sites = sitesOf(pspId);
            for (int i = 0; i < missing.size(); i += LegalHold.MAX_ITEMS) {
                Set<UUID> chunk = new LinkedHashSet<>(missing.subList(i, Math.min(missing.size(), i + LegalHold.MAX_ITEMS)));
                LegalHold h = holds.save(new LegalHold(UUID.randomUUID(), wf, chunk, now, by));
                for (String site : sites) {
                    siteAcks.save(new LegalHoldSiteAck(h.getHoldId(), site, LegalHoldSiteAck.PLACE));
                }
                placed.add(h.getHoldId());
            }
            return new Coverage(List.copyOf(placed), missing.size(), wanted.size() - missing.size());
        });
        if (result != null && !result.placedHoldIds().isEmpty()) {
            log.info("Placed {} legal hold(s) for {} {} covering {} decision record(s)", result.placedHoldIds().size(),
                    type, workflowId, result.newlyCovered());
            List<UUID> toSend = result.placedHoldIds();
            afterCommit(() -> deliver(toSend));
        }
        return result;
    }

    /**
     * The workflow record has ended (SAR or CTR filed, case closed): its holds are released once
     * {@code basis + retention_years} has passed. Only sets the basis where it is not set yet.
     */
    public int markWorkflowEnded(LegalHoldWorkflow.Type type, long workflowId, Instant basis) {
        Integer n = required.execute(status -> {
            Optional<LegalHoldWorkflow> wf = workflows.findByWorkflowTypeAndWorkflowId(type.name(), workflowId);
            if (wf.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (LegalHold h : holds.findByWorkflowRefId(wf.get().getId())) {
                if (h.isActive() && h.getReleaseBasisAt() == null) {
                    h.setReleaseBasisAt(basis);
                    holds.save(h);
                    count++;
                }
            }
            return count;
        });
        return n == null ? 0 : n;
    }

    /** The workflow record was reopened (case reopened): its holds stay until it ends again. */
    public int markWorkflowReopened(LegalHoldWorkflow.Type type, long workflowId) {
        Integer n = required.execute(status -> {
            Optional<LegalHoldWorkflow> wf = workflows.findByWorkflowTypeAndWorkflowId(type.name(), workflowId);
            if (wf.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (LegalHold h : holds.findByWorkflowRefId(wf.get().getId())) {
                if (h.isActive() && h.getReleaseBasisAt() != null) {
                    h.setReleaseBasisAt(null);
                    holds.save(h);
                    count++;
                }
            }
            return count;
        });
        return n == null ? 0 : n;
    }

    // ── releasing ────────────────────────────────────────────────────────────────────────────────

    /**
     * Releases every ACTIVE hold of a workflow record now (e.g. {@code PLACED_IN_ERROR} when a SAR
     * draft is deleted). Joins the caller's transaction; the notifies go out after it commits.
     *
     * @return holds released
     */
    public int releaseWorkflow(LegalHoldWorkflow.Type type, long workflowId, LegalHold.ReleaseReason reason,
                               String releasedBy) {
        String by = safeUser(releasedBy);
        List<UUID> released = required.execute(status -> {
            Optional<LegalHoldWorkflow> wf = workflows.findByWorkflowTypeAndWorkflowId(type.name(), workflowId);
            if (wf.isEmpty()) {
                return List.<UUID>of();
            }
            List<UUID> out = new ArrayList<>();
            for (LegalHold h : holds.findByWorkflowRefId(wf.get().getId())) {
                if (h.isActive()) {
                    releaseRow(h, by, reason);
                    out.add(h.getHoldId());
                }
            }
            return out;
        });
        List<UUID> ids = released == null ? List.of() : released;
        if (!ids.isEmpty()) {
            log.info("Released {} legal hold(s) of {} {} ({})", ids.size(), type, workflowId, reason);
            afterCommit(() -> deliver(ids));
        }
        return ids.size();
    }

    /**
     * Releases ACTIVE holds whose workflow ended at least the PSP's {@code retention_years} ago
     * (§8.3/§8.4: filed documents and closed workflow records keep their data for retention years
     * from filing or closure). SAR/CTR holds release with {@code FILING_RETENTION_ELAPSED}, case
     * holds with {@code CASE_CLOSED}.
     *
     * @return holds released
     */
    public int releaseDue() {
        Instant now = clock.instant();
        Map<Long, Integer> yearsByPsp = new HashMap<>();
        List<UUID> released = required.execute(status -> {
            List<UUID> out = new ArrayList<>();
            for (LegalHold h : holds.findByStateAndReleaseBasisAtIsNotNull(LegalHold.State.ACTIVE.name())) {
                int years = yearsByPsp.computeIfAbsent(h.getPspId(), psp -> Math.max(
                        PspDataSettingsService.MIN_RETENTION_YEARS, dataSettings.retentionState(psp).years()));
                Instant due = OffsetDateTime.ofInstant(h.getReleaseBasisAt(), ZoneOffset.UTC).plusYears(years).toInstant();
                if (due.isAfter(now)) {
                    continue;
                }
                LegalHold.ReleaseReason reason = "CASE".equals(h.getReasonCode())
                        ? LegalHold.ReleaseReason.CASE_CLOSED : LegalHold.ReleaseReason.FILING_RETENTION_ELAPSED;
                releaseRow(h, RETENTION_JOB, reason);
                out.add(h.getHoldId());
            }
            return out;
        });
        List<UUID> ids = released == null ? List.of() : released;
        if (!ids.isEmpty()) {
            log.info("Released {} legal hold(s) whose retention after workflow end has elapsed", ids.size());
            afterCommit(() -> deliver(ids));
        }
        return ids.size();
    }

    /** Marks one hold released and switches every site row to expect the release ack. */
    private void releaseRow(LegalHold h, String by, LegalHold.ReleaseReason reason) {
        h.release(clock.instant(), by, reason);
        holds.save(h);
        for (LegalHoldSiteAck row : siteAcks.findByHoldIdIn(List.of(h.getHoldId()))) {
            row.expect(LegalHoldSiteAck.RELEASE);
            siteAcks.save(row);
        }
    }

    // ── delivery ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Sends the current notify of each hold (place for ACTIVE, release for RELEASED) to every live
     * session of its PSP and records the sends per site. Without the tunnel (or with no live
     * session) the rows stay unconfirmed and are sent after the next {@code hello.ack}.
     */
    public void deliver(Collection<UUID> holdIds) {
        TunnelNotifier n = notifier.getIfAvailable();
        if (n == null || holdIds.isEmpty()) {
            return;
        }
        requiresNew.executeWithoutResult(status -> {
            for (LegalHold h : holds.findByHoldIdIn(holdIds)) {
                List<TunnelNotifier.Delivery> sent;
                try {
                    sent = n.notifyPsp(h.getPspId(), body(h));
                } catch (RuntimeException e) {
                    log.warn("Legal hold {} could not be sent: {}", h.getHoldId(), e.getClass().getSimpleName());
                    continue;
                }
                recordSends(h, sent);
            }
        });
    }

    /**
     * §7.10: after a site's {@code hello.ack}, re-sends every hold of the PSP the site has not
     * confirmed: ACTIVE holds not acknowledged APPLIED (including holds placed before the site
     * existed), and released holds whose release the site has not acknowledged.
     *
     * @return notifies sent
     */
    public int resendUnconfirmed(TunnelSessionListener.TunnelSessionInfo session, Duration olderThan) {
        TunnelNotifier n = notifier.getIfAvailable();
        if (n == null) {
            return 0;
        }
        Instant cutoff = olderThan == null ? null : clock.instant().minus(olderThan);
        Integer count = requiresNew.execute(status -> {
            List<LegalHold> all = holds.findByPspId(session.pspId());
            if (all.isEmpty()) {
                return 0;
            }
            Map<UUID, LegalHoldSiteAck> rows = new HashMap<>();
            for (LegalHoldSiteAck r : siteAcks.findByHoldIdIn(all.stream().map(LegalHold::getHoldId).toList())) {
                if (r.getSiteId().equals(session.siteId())) {
                    rows.put(r.getHoldId(), r);
                }
            }
            int sent = 0;
            for (LegalHold h : all) {
                LegalHoldSiteAck row = rows.get(h.getHoldId());
                if (h.isActive()) {
                    if (row == null) {
                        row = siteAcks.save(new LegalHoldSiteAck(h.getHoldId(), session.siteId(), LegalHoldSiteAck.PLACE));
                    }
                } else if (row == null) {
                    // The site never received this hold: nothing to release there.
                    continue;
                }
                if (row.isConfirmed() || (cutoff != null && row.getSentAt() != null && row.getSentAt().isAfter(cutoff))) {
                    continue;
                }
                Optional<TunnelNotifier.Delivery> d;
                try {
                    d = n.notifySession(session.pspId(), session.sessionId(), body(h));
                } catch (RuntimeException e) {
                    log.warn("Legal hold {} could not be re-sent to session {}: {}", h.getHoldId(),
                            session.sessionId(), e.getClass().getSimpleName());
                    continue;
                }
                if (d.isPresent()) {
                    row.sent(d.get().notifyId(), clock.instant());
                    siteAcks.save(row);
                    sent++;
                }
            }
            return sent;
        });
        int c = count == null ? 0 : count;
        if (c > 0) {
            log.info("Re-sent {} unconfirmed legal hold notify(s) to site {} (session {})", c, session.siteId(),
                    session.sessionId());
        }
        return c;
    }

    /**
     * Sweep for live sessions: re-sends holds that a connected site has not confirmed within
     * {@code olderThan} (an ack lost, or an Edge that refused the notify while its ledger was down).
     *
     * @return notifies sent
     */
    public int resendStale(Duration olderThan) {
        TunnelRegistry reg = registry.getIfAvailable();
        if (reg == null || notifier.getIfAvailable() == null) {
            return 0;
        }
        int sent = 0;
        Instant now = clock.instant();
        for (Long psp : holds.pspsWithHolds()) {
            Set<String> seenSites = new TreeSet<>();
            for (TunnelRegistry.Entry e : reg.liveEntries(psp, now)) {
                if (seenSites.add(e.siteId())) {
                    sent += resendUnconfirmed(new TunnelSessionListener.TunnelSessionInfo(psp, e.siteId(), e.edgeId(),
                            e.sessionId(), e.cpInstance()), olderThan);
                }
            }
        }
        return sent;
    }

    /**
     * Records a {@code hold.ack} from a site and compares its {@code activeHoldDigest} with the
     * cloud's own set (§7.10). On divergence the site's unconfirmed holds not sent within
     * {@link #RESEND_AFTER} are re-sent.
     */
    public void recordAck(TunnelSessionListener.TunnelSessionInfo session, String notifyId, ObjectNode item) {
        UUID holdId;
        try {
            holdId = UUID.fromString(item.path("holdId").asText());
        } catch (IllegalArgumentException e) {
            log.warn("hold.ack with a malformed holdId discarded (session {})", session.sessionId());
            return;
        }
        String state = item.path("state").asText();
        long matched = item.path("matchedRows").asLong(0);
        String digest = item.path("activeHoldDigest").asText(null);
        Boolean diverged = requiresNew.execute(status -> {
            Optional<LegalHold> hold = holds.findById(holdId);
            if (hold.isEmpty() || hold.get().getPspId() != session.pspId()) {
                log.warn("hold.ack for a hold this PSP does not own discarded (session {})", session.sessionId());
                return false;
            }
            LegalHoldSiteAck row = siteAcks.findByHoldIdAndSiteId(holdId, session.siteId())
                    .orElseGet(() -> new LegalHoldSiteAck(holdId, session.siteId(),
                            hold.get().isActive() ? LegalHoldSiteAck.PLACE : LegalHoldSiteAck.RELEASE));
            String expected = expectedDigest(session.pspId());
            boolean match = expected.equals(digest);
            row.acked(state, clock.instant(), matched, digest, match);
            siteAcks.save(row);
            if (!row.isConfirmed()) {
                log.warn("Legal hold {} at site {}: ack {} does not confirm the last {}", holdId, session.siteId(),
                        state, row.getNotifyType());
            }
            return !match;
        });
        if (Boolean.TRUE.equals(diverged)) {
            log.info("Site {} reports a different active hold set; re-sending its unconfirmed holds", session.siteId());
            resendUnconfirmed(session, RESEND_AFTER);
        }
    }

    /** §7.10 digest of the PSP's active holds: SHA-256 hex of the sorted, newline-joined ids. */
    public String expectedDigest(long pspId) {
        List<String> ids = new ArrayList<>(holds.activeHoldIds(pspId).stream().map(UUID::toString).toList());
        ids.sort(null);
        return Jcs.sha256Hex(String.join("\n", ids).getBytes(StandardCharsets.UTF_8));
    }

    /** The PSP's active hold count and the ids it covers, for diagnostics and tests. */
    public Set<UUID> activelyHeldIds(long pspId) {
        Set<UUID> out = new LinkedHashSet<>();
        for (LegalHold h : holds.findByPspId(pspId)) {
            if (h.isActive()) {
                out.addAll(h.getEdgeAssessmentIds());
            }
        }
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** The notify body of a hold's current state ({@code notify/hold.place|hold.release} schemas). */
    static ObjectNode body(LegalHold h) {
        ObjectNode b = JsonNodeFactory.instance.objectNode();
        if (h.isActive()) {
            b.put("type", "hold.place");
            b.put("holdId", h.getHoldId().toString());
            b.put("placedAt", TunnelTime.formatSeconds(h.getPlacedAt()));
            b.put("placedBy", h.getPlacedBy());
            b.put("reasonCode", h.getReasonCode());
            b.put("reference", h.getReference());
            ArrayNode ids = b.putObject("scope").putArray("edgeAssessmentIds");
            h.getEdgeAssessmentIds().stream().map(UUID::toString).sorted().forEach(ids::add);
        } else {
            b.put("type", "hold.release");
            b.put("holdId", h.getHoldId().toString());
            b.put("releasedAt", TunnelTime.formatSeconds(h.getReleasedAt()));
            b.put("releasedBy", h.getReleasedBy());
            b.put("reasonCode", h.getReleaseReason());
        }
        return b;
    }

    /** Records each delivery against the hold's row for that site (creating rows for new sites). */
    private void recordSends(LegalHold h, List<TunnelNotifier.Delivery> sent) {
        Map<String, LegalHoldSiteAck> rows = new LinkedHashMap<>();
        siteAcks.findByHoldIdIn(List.of(h.getHoldId())).forEach(r -> rows.put(r.getSiteId(), r));
        Instant now = clock.instant();
        for (TunnelNotifier.Delivery d : sent) {
            LegalHoldSiteAck row = rows.get(d.siteId());
            if (row == null) {
                row = new LegalHoldSiteAck(h.getHoldId(), d.siteId(),
                        h.isActive() ? LegalHoldSiteAck.PLACE : LegalHoldSiteAck.RELEASE);
                rows.put(d.siteId(), row);
            }
            row.sent(d.notifyId(), now);
            siteAcks.save(row);
        }
    }

    /** The workflow row, locked; created first (in its own transaction) when it does not exist. */
    private LegalHoldWorkflow lockOrCreate(long pspId, LegalHoldWorkflow.Type type, long workflowId,
                                           String reasonCode, String reference) {
        Optional<LegalHoldWorkflow> locked = workflows.lockByWorkflow(type.name(), workflowId);
        if (locked.isPresent()) {
            return locked.get();
        }
        try {
            requiresNew.executeWithoutResult(s -> workflows.saveAndFlush(
                    new LegalHoldWorkflow(pspId, type, workflowId, reasonCode, reference)));
        } catch (DataIntegrityViolationException raced) {
            // Another instance created it first: lock theirs.
            log.debug("Legal hold workflow row for {} {} created concurrently", type, workflowId);
        }
        return workflows.lockByWorkflow(type.name(), workflowId)
                .orElseThrow(() -> new IllegalStateException("legal hold workflow row missing after create"));
    }

    /** Edge Sites of the PSP's ACTIVE nodes (a site's id, or the node id for a single-node site). */
    private Set<String> sitesOf(long pspId) {
        Set<String> out = new TreeSet<>();
        for (EdgeNode node : nodes.findByPspIdAndStatus(pspId, EdgeNodeStatus.ACTIVE)) {
            out.add(node.effectiveSiteId());
        }
        return out;
    }

    /** A schema-valid workflowRef: the given reference, else {@code <TYPE>-<id>}. */
    static String safeRef(String reference, LegalHoldWorkflow.Type type, long workflowId) {
        if (reference != null && WORKFLOW_REF.matcher(reference).matches()) {
            return reference;
        }
        return type.name() + "-" + workflowId;
    }

    /** A schema-valid userRef, else the system actor of this service. */
    static String safeUser(String userRef) {
        return userRef != null && USER_REF.matcher(userRef).matches() ? userRef : "system:legal-holds";
    }

    /** Runs {@code r} after the current transaction commits (immediately when there is none). */
    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** Sends once the hold rows are durable; a failure leaves them for the re-send paths. */
                @Override
                public void afterCommit() {
                    runSafely(r);
                }
            });
        } else {
            runSafely(r);
        }
    }

    /** Runs a delivery, logging (not propagating) a failure: the rows stay unconfirmed. */
    private static void runSafely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            log.warn("Legal hold delivery failed: {}", e.getClass().getSimpleName());
        }
    }
}
