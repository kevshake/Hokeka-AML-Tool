package com.posgateway.aml.service.residency;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.residency.PspDataSettingChange;
import com.posgateway.aml.entity.residency.PspDataSettingChange.Setting;
import com.posgateway.aml.entity.residency.PspDataSettingChange.Status;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.residency.PspDataSettingChangeRepository;
import com.posgateway.aml.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PSP data residency settings with maker-checker (contract sections 8.1, 9, 10):
 * {@code retention_years} (5..25), Hokeka support access and cross-PSP intelligence opt-in.
 *
 * <p><b>Maker-checker</b> follows the rule-governance pattern ({@code RuleGovernanceService}): a PSP
 * admin proposes ({@code PENDING_APPROVAL}); a different PSP admin, MLRO or compliance officer of
 * the same PSP approves or rejects with a reason. Every step is written to the tamper-evident audit
 * log.
 *
 * <p><b>Timing.</b> A retention increase takes effect on approval. A reduction takes effect no sooner
 * than {@value #REDUCTION_DELAY_DAYS} days after approval: until then it is {@code APPROVED} with an
 * {@code effectiveAt}, the Edge config carries it as {@code retention.pendingChange}, and a
 * scheduled job applies it when due. Enabling support access or opting in needs a checker;
 * <b>withdrawing</b> either consent takes effect at once on a single admin's action, because a
 * PSP's revocation of access to its own data must never wait for a second person.
 */
@Service
public class PspDataSettingsService {

    private static final Logger log = LoggerFactory.getLogger(PspDataSettingsService.class);

    /** FATF R.11 retention floor. */
    public static final int MIN_RETENTION_YEARS = 5;
    /** Retention cap (DB CHECK in V261). */
    public static final int MAX_RETENTION_YEARS = 25;
    /** Minimum delay between approving a retention reduction and it taking effect. */
    public static final int REDUCTION_DELAY_DAYS = 30;

    private static final int MAX_REASON_LENGTH = 2000;

    private final PspRepository psps;
    private final PspDataSettingChangeRepository changes;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public PspDataSettingsService(PspRepository psps,
                                  PspDataSettingChangeRepository changes,
                                  AuditLogService audit,
                                  ApplicationEventPublisher events) {
        this(psps, changes, audit, events, Clock.systemUTC());
    }

    /** Test constructor with an explicit clock, so the 30-day rule can be exercised. */
    public PspDataSettingsService(PspRepository psps,
                                  PspDataSettingChangeRepository changes,
                                  AuditLogService audit,
                                  ApplicationEventPublisher events,
                                  Clock clock) {
        this.psps = psps;
        this.changes = changes;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ── views ────────────────────────────────────────────────────────────────────────────────────

    /** Retention in force plus the approved-but-waiting reduction, as the Edge config needs it. */
    public record RetentionState(int years, Instant effectiveSince, PendingRetention pending) {}

    /** An approved retention reduction that is not yet in force. */
    public record PendingRetention(int years, Instant approvedAt, Instant effectiveAt) {}

    /** All three settings of a PSP, plus open change requests, for the Console. */
    public record DataSettingsView(
            Long pspId,
            int retentionYears,
            Instant retentionEffectiveSince,
            PendingRetention pendingRetention,
            boolean supportAccessEnabled,
            Instant supportAccessChangedAt,
            boolean crossPspOptIn,
            Instant crossPspOptInChangedAt,
            int minRetentionYears,
            int maxRetentionYears,
            int reductionDelayDays,
            List<ChangeView> openChanges) {}

    /** One change request as shown in the Console. */
    public record ChangeView(
            Long id,
            String setting,
            String oldValue,
            String newValue,
            String status,
            String reason,
            String requestedBy,
            Instant requestedAt,
            String reviewedBy,
            Instant reviewedAt,
            String reviewReason,
            Instant effectiveAt,
            Instant appliedAt) {

        /** Maps the entity to its Console view. */
        static ChangeView of(PspDataSettingChange c) {
            return new ChangeView(c.getId(), c.getSetting().name(), c.getOldValue(), c.getNewValue(),
                    c.getStatus().name(), c.getReason(), c.getRequestedByUsername(), c.getRequestedAt(),
                    c.getReviewedByUsername(), c.getReviewedAt(), c.getReviewReason(), c.getEffectiveAt(),
                    c.getAppliedAt());
        }
    }

    /**
     * Current settings of a PSP. Applies any approved change whose effective time has passed first,
     * so the view never shows a stale value while the scheduled job has not run yet.
     */
    @Transactional
    public DataSettingsView view(Long pspId) {
        Instant now = clock.instant();
        applyDueChanges(pspId, now);
        Psp psp = requirePsp(pspId);
        List<ChangeView> open = changes.findByPspIdAndStatusInOrderByRequestedAtAsc(
                        pspId, List.of(Status.PENDING_APPROVAL, Status.APPROVED))
                .stream().map(ChangeView::of).toList();
        return new DataSettingsView(
                pspId,
                retentionOf(psp),
                psp.getRetentionEffectiveSince(),
                pendingRetention(pspId),
                Boolean.TRUE.equals(psp.getSupportAccessEnabled()),
                psp.getSupportAccessChangedAt(),
                Boolean.TRUE.equals(psp.getCrossPspOptIn()),
                psp.getCrossPspOptInChangedAt(),
                MIN_RETENTION_YEARS,
                MAX_RETENTION_YEARS,
                REDUCTION_DELAY_DAYS,
                open);
    }

    /** Retention in force (after applying due changes) and any waiting reduction. */
    @Transactional
    public RetentionState retentionState(Long pspId) {
        applyDueChanges(pspId, clock.instant());
        Psp psp = requirePsp(pspId);
        return new RetentionState(retentionOf(psp), psp.getRetentionEffectiveSince(), pendingRetention(pspId));
    }

    /** Every change request of a PSP, newest first. */
    @Transactional(readOnly = true)
    public List<ChangeView> history(Long pspId) {
        requirePsp(pspId);
        return changes.findByPspIdOrderByRequestedAtDesc(pspId).stream().map(ChangeView::of).toList();
    }

    // ── maker ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Proposes a new retention period. Proposing the value already in force while a reduction is
     * waiting is how a PSP cancels that reduction (approval supersedes it).
     */
    @Transactional
    public ChangeView proposeRetention(Long pspId, Integer years, String reason, User maker) {
        if (years == null || years < MIN_RETENTION_YEARS || years > MAX_RETENTION_YEARS) {
            throw new IllegalArgumentException("retentionYears must be between " + MIN_RETENTION_YEARS
                    + " and " + MAX_RETENTION_YEARS);
        }
        Instant now = clock.instant();
        applyDueChanges(pspId, now);
        Psp psp = requirePsp(pspId);
        int current = retentionOf(psp);
        boolean reductionWaiting = !changes.findByPspIdAndSettingAndStatus(
                pspId, Setting.RETENTION_YEARS, Status.APPROVED).isEmpty();
        if (years == current && !reductionWaiting) {
            throw new IllegalArgumentException("Retention is already " + current + " years");
        }
        requireNoPending(pspId, Setting.RETENTION_YEARS);
        PspDataSettingChange change = newChange(pspId, Setting.RETENTION_YEARS,
                Integer.toString(current), Integer.toString(years), reason, maker, now);
        change = changes.save(change);
        auditChange(maker, "PSP_DATA_SETTING_PROPOSED", change, null);
        return ChangeView.of(change);
    }

    /** Proposes enabling (maker-checker) or withdraws (immediate) Hokeka support access. */
    @Transactional
    public ChangeView proposeSupportAccess(Long pspId, Boolean enabled, String reason, User maker) {
        return proposeToggle(pspId, Setting.SUPPORT_ACCESS, enabled, reason, maker);
    }

    /** Proposes opting in (maker-checker) or opts out (immediate) of cross-PSP intelligence. */
    @Transactional
    public ChangeView proposeCrossPspOptIn(Long pspId, Boolean optIn, String reason, User maker) {
        return proposeToggle(pspId, Setting.CROSS_PSP_OPT_IN, optIn, reason, maker);
    }

    /**
     * Shared toggle flow. Turning a consent on creates a PENDING_APPROVAL request; turning it off is
     * applied at once (and supersedes any pending request to turn it on).
     */
    private ChangeView proposeToggle(Long pspId, Setting setting, Boolean value, String reason, User maker) {
        if (value == null) {
            throw new IllegalArgumentException(setting == Setting.SUPPORT_ACCESS
                    ? "enabled is required" : "optIn is required");
        }
        Instant now = clock.instant();
        Psp psp = requirePsp(pspId);
        boolean current = toggleOf(psp, setting);
        if (value == current) {
            throw new IllegalArgumentException(describe(setting) + " is already " + (current ? "on" : "off"));
        }
        if (!value) {
            changes.findFirstByPspIdAndSettingAndStatus(pspId, setting, Status.PENDING_APPROVAL)
                    .ifPresent(pending -> supersede(pending, maker, now, "Consent withdrawn before approval"));
            PspDataSettingChange change = newChange(pspId, setting, "true", "false", reason, maker, now);
            change.setStatus(Status.APPLIED);
            change.setEffectiveAt(now);
            change.setAppliedAt(now);
            change.setReviewReason("Consent withdrawal takes effect immediately");
            change = changes.save(change);
            applyToggle(psp, setting, false, now);
            auditChange(maker, "PSP_DATA_SETTING_APPLIED", change, null);
            return ChangeView.of(change);
        }
        requireNoPending(pspId, setting);
        PspDataSettingChange change = newChange(pspId, setting, "false", "true", reason, maker, now);
        change = changes.save(change);
        auditChange(maker, "PSP_DATA_SETTING_PROPOSED", change, null);
        return ChangeView.of(change);
    }

    // ── checker ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Approves a pending change. The checker must differ from the maker. A retention reduction is
     * scheduled {@value #REDUCTION_DELAY_DAYS} days out; everything else takes effect now.
     */
    @Transactional
    public ChangeView approve(Long pspId, Long changeId, String reason, User checker) {
        Instant now = clock.instant();
        PspDataSettingChange change = requirePending(pspId, changeId);
        requireDistinctChecker(change, checker);
        String reviewReason = requireReason(reason);
        change.setReviewedBy(checker.getId());
        change.setReviewedByUsername(checker.getUsername());
        change.setReviewedAt(now);
        change.setReviewReason(reviewReason);
        Psp psp = requirePsp(pspId);

        if (change.getSetting() == Setting.RETENTION_YEARS) {
            int target = Integer.parseInt(change.getNewValue());
            int current = retentionOf(psp);
            // Any reduction still waiting is replaced by this decision, whatever its direction.
            for (PspDataSettingChange waiting : changes.findByPspIdAndSettingAndStatus(
                    pspId, Setting.RETENTION_YEARS, Status.APPROVED)) {
                supersede(waiting, checker, now, "Superseded by change " + change.getId());
            }
            if (target < current) {
                change.setStatus(Status.APPROVED);
                change.setEffectiveAt(now.plus(Duration.ofDays(REDUCTION_DELAY_DAYS)));
                change = changes.save(change);
                auditChange(checker, "PSP_DATA_SETTING_APPROVED", change, null);
                // The pending reduction is itself part of the Edge config (retention.pendingChange).
                events.publishEvent(new PspDataSettingsChangedEvent(pspId, Setting.RETENTION_YEARS.name()));
                return ChangeView.of(change);
            }
            change.setEffectiveAt(now);
            change.setStatus(Status.APPLIED);
            change.setAppliedAt(now);
            change = changes.save(change);
            applyRetention(psp, target, now);
            auditChange(checker, "PSP_DATA_SETTING_APPLIED", change, null);
            return ChangeView.of(change);
        }

        boolean target = Boolean.parseBoolean(change.getNewValue());
        change.setEffectiveAt(now);
        change.setStatus(Status.APPLIED);
        change.setAppliedAt(now);
        change = changes.save(change);
        applyToggle(psp, change.getSetting(), target, now);
        auditChange(checker, "PSP_DATA_SETTING_APPLIED", change, null);
        return ChangeView.of(change);
    }

    /** Rejects a pending change. The checker must differ from the maker and give a reason. */
    @Transactional
    public ChangeView reject(Long pspId, Long changeId, String reason, User checker) {
        Instant now = clock.instant();
        PspDataSettingChange change = requirePending(pspId, changeId);
        requireDistinctChecker(change, checker);
        change.setReviewedBy(checker.getId());
        change.setReviewedByUsername(checker.getUsername());
        change.setReviewedAt(now);
        change.setReviewReason(requireReason(reason));
        change.setStatus(Status.REJECTED);
        change = changes.save(change);
        auditChange(checker, "PSP_DATA_SETTING_REJECTED", change, null);
        return ChangeView.of(change);
    }

    // ── scheduled apply ──────────────────────────────────────────────────────────────────────────

    /** Applies every approved change whose effective time has passed, across all PSPs. */
    @Scheduled(cron = "${residency.settings.apply-cron:0 */5 * * * *}")
    @Transactional
    public int applyDueChanges() {
        Instant now = clock.instant();
        int applied = 0;
        for (PspDataSettingChange due : changes.findByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
                Status.APPROVED, now)) {
            applied += applyApproved(due, now) ? 1 : 0;
        }
        if (applied > 0) {
            log.info("Applied {} approved PSP data setting change(s)", applied);
        }
        return applied;
    }

    /** Applies the due approved changes of one PSP (used before every read). */
    @Transactional
    public int applyDueChanges(Long pspId, Instant now) {
        int applied = 0;
        for (PspDataSettingChange due : changes.findByPspIdAndStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
                pspId, Status.APPROVED, now)) {
            applied += applyApproved(due, now) ? 1 : 0;
        }
        return applied;
    }

    /** Writes one due, approved change to the PSP row and marks it APPLIED. */
    private boolean applyApproved(PspDataSettingChange due, Instant now) {
        Psp psp = psps.findById(due.getPspId()).orElse(null);
        if (psp == null) {
            log.warn("Approved data setting change {} refers to missing PSP {}", due.getId(), due.getPspId());
            return false;
        }
        due.setStatus(Status.APPLIED);
        due.setAppliedAt(now);
        changes.save(due);
        if (due.getSetting() == Setting.RETENTION_YEARS) {
            // The in-force value changes at effectiveAt, not at the (possibly later) job run.
            applyRetention(psp, Integer.parseInt(due.getNewValue()),
                    due.getEffectiveAt() != null ? due.getEffectiveAt() : now);
        } else {
            applyToggle(psp, due.getSetting(), Boolean.parseBoolean(due.getNewValue()), now);
        }
        auditChange(null, "PSP_DATA_SETTING_APPLIED", due, "Scheduled: effective time reached");
        return true;
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────────

    /** Writes a retention value to the PSP row and announces the config change. */
    private void applyRetention(Psp psp, int years, Instant effectiveSince) {
        psp.setRetentionYears(Math.max(MIN_RETENTION_YEARS, years));
        psp.setRetentionEffectiveSince(effectiveSince);
        psps.save(psp);
        events.publishEvent(new PspDataSettingsChangedEvent(psp.getPspId(), Setting.RETENTION_YEARS.name()));
    }

    /** Writes a consent flag to the PSP row and announces the config change. */
    private void applyToggle(Psp psp, Setting setting, boolean value, Instant now) {
        if (setting == Setting.SUPPORT_ACCESS) {
            psp.setSupportAccessEnabled(value);
            psp.setSupportAccessChangedAt(now);
        } else {
            psp.setCrossPspOptIn(value);
            psp.setCrossPspOptInChangedAt(now);
        }
        psps.save(psp);
        events.publishEvent(new PspDataSettingsChangedEvent(psp.getPspId(), setting.name()));
    }

    /** The approved reduction waiting to take effect, if any. */
    private PendingRetention pendingRetention(Long pspId) {
        return changes.findByPspIdAndSettingAndStatus(pspId, Setting.RETENTION_YEARS, Status.APPROVED).stream()
                .filter(c -> c.getEffectiveAt() != null)
                .findFirst()
                .map(c -> new PendingRetention(Integer.parseInt(c.getNewValue()), c.getReviewedAt(), c.getEffectiveAt()))
                .orElse(null);
    }

    /** Marks a pending or waiting change as superseded, with an audit entry. */
    private void supersede(PspDataSettingChange change, User actor, Instant now, String why) {
        change.setStatus(Status.SUPERSEDED);
        if (change.getReviewedAt() == null) {
            change.setReviewedAt(now);
            change.setReviewedBy(actor == null ? null : actor.getId());
            change.setReviewedByUsername(actor == null ? "SYSTEM" : actor.getUsername());
            change.setReviewReason(why);
        }
        changes.save(change);
        auditChange(actor, "PSP_DATA_SETTING_SUPERSEDED", change, why);
    }

    /** Builds a PENDING_APPROVAL request. */
    private PspDataSettingChange newChange(Long pspId, Setting setting, String oldValue, String newValue,
                                           String reason, User maker, Instant now) {
        PspDataSettingChange change = new PspDataSettingChange();
        change.setPspId(pspId);
        change.setSetting(setting);
        change.setOldValue(oldValue);
        change.setNewValue(newValue);
        change.setStatus(Status.PENDING_APPROVAL);
        change.setReason(requireReason(reason));
        change.setRequestedBy(maker.getId());
        change.setRequestedByUsername(maker.getUsername());
        change.setRequestedAt(now);
        return change;
    }

    /** 409 when a request for the same setting is already waiting for a checker. */
    private void requireNoPending(Long pspId, Setting setting) {
        if (changes.findFirstByPspIdAndSettingAndStatus(pspId, setting, Status.PENDING_APPROVAL).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A change to " + describe(setting) + " is already awaiting approval");
        }
    }

    /** Loads a change of this PSP that is still PENDING_APPROVAL (404 / 409 otherwise). */
    private PspDataSettingChange requirePending(Long pspId, Long changeId) {
        PspDataSettingChange change = changes.findById(changeId)
                .filter(c -> c.getPspId().equals(pspId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change request not found"));
        if (change.getStatus() != Status.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Change request is " + change.getStatus() + ", not awaiting approval");
        }
        return change;
    }

    /** Maker-checker: the reviewer must be a different person from the requester. */
    private static void requireDistinctChecker(PspDataSettingChange change, User checker) {
        boolean sameId = change.getRequestedBy() != null && change.getRequestedBy().equals(checker.getId());
        boolean sameName = change.getRequestedByUsername() != null
                && change.getRequestedByUsername().equalsIgnoreCase(checker.getUsername());
        if (sameId || sameName) {
            throw new SecurityException("The person who proposed a change cannot approve or reject it (maker-checker)");
        }
    }

    /** A non-blank justification of bounded length. */
    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
        return trimmed;
    }

    /** Loads the PSP or answers 404. */
    private Psp requirePsp(Long pspId) {
        return psps.findById(pspId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PSP not found"));
    }

    /** In-force retention, clamped to the floor. */
    private static int retentionOf(Psp psp) {
        Integer years = psp.getRetentionYears();
        return years == null ? MIN_RETENTION_YEARS : Math.max(MIN_RETENTION_YEARS, years);
    }

    /** Current value of a consent flag. */
    private static boolean toggleOf(Psp psp, Setting setting) {
        return setting == Setting.SUPPORT_ACCESS
                ? Boolean.TRUE.equals(psp.getSupportAccessEnabled())
                : Boolean.TRUE.equals(psp.getCrossPspOptIn());
    }

    /** Human label of a setting for messages. */
    private static String describe(Setting setting) {
        return switch (setting) {
            case RETENTION_YEARS -> "data retention";
            case SUPPORT_ACCESS -> "Hokeka support access";
            case CROSS_PSP_OPT_IN -> "cross-PSP intelligence";
        };
    }

    /** Writes one audit-log row for a change request event. */
    private void auditChange(User actor, String action, PspDataSettingChange change, String note) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("changeId", change.getId());
        after.put("pspId", change.getPspId());
        after.put("setting", change.getSetting().name());
        after.put("oldValue", change.getOldValue());
        after.put("newValue", change.getNewValue());
        after.put("status", change.getStatus().name());
        after.put("requestedBy", change.getRequestedByUsername());
        after.put("reviewedBy", change.getReviewedByUsername());
        after.put("effectiveAt", change.getEffectiveAt() == null ? null : change.getEffectiveAt().toString());
        List<String> reasons = new ArrayList<>();
        if (change.getReason() != null) reasons.add(change.getReason());
        if (change.getReviewReason() != null) reasons.add(change.getReviewReason());
        if (note != null) reasons.add(note);
        audit.logAction(actor, action, "PSP_DATA_SETTING", String.valueOf(change.getPspId()),
                null, after, null, String.join(" | ", reasons));
    }
}
