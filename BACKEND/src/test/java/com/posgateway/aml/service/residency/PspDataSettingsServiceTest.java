package com.posgateway.aml.service.residency;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.residency.PspDataSettingChange;
import com.posgateway.aml.entity.residency.PspDataSettingChange.Setting;
import com.posgateway.aml.entity.residency.PspDataSettingChange.Status;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.residency.PspDataSettingChangeRepository;
import com.posgateway.aml.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Maker-checker and timing rules of PSP data settings (contract 8.1, 9, 10): the checker must
 * differ from the maker, an increase applies on approval, a reduction no sooner than 30 days later,
 * consent withdrawal applies at once, and every step is audited.
 */
class PspDataSettingsServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-16T08:00:00Z");

    private final List<PspDataSettingChange> rows = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private MutableClock clock;
    private Psp psp;
    private AuditLogService audit;
    private ApplicationEventPublisher events;
    private PspDataSettingsService service;
    private User maker;
    private User checker;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        psp = new Psp();
        psp.setPspId(12L);
        psp.setRetentionYears(5);
        psp.setRetentionEffectiveSince(T0.minus(Duration.ofDays(400)));
        psp.setSupportAccessEnabled(false);
        psp.setCrossPspOptIn(false);
        PspRepository psps = mock(PspRepository.class);
        when(psps.findById(12L)).thenReturn(Optional.of(psp));
        when(psps.save(any(Psp.class))).thenAnswer(i -> i.getArgument(0));
        audit = mock(AuditLogService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new PspDataSettingsService(psps, inMemoryChanges(), audit, events, clock);
        maker = user(1L, "alice", "PSP_ADMIN");
        checker = user(2L, "bob", "MLRO");
    }

    @Test
    void increaseNeedsASecondPersonAndAppliesOnApproval() {
        PspDataSettingsService.ChangeView pending = service.proposeRetention(12L, 10, "Regulator asked for 10 years", maker);
        assertEquals("PENDING_APPROVAL", pending.status());
        assertEquals(5, psp.getRetentionYears(), "nothing changes before approval");

        assertThrows(SecurityException.class, () -> service.approve(12L, pending.id(), "self approval", maker));
        User makerUnderAnotherId = user(99L, "ALICE", "PSP_ADMIN");
        assertThrows(SecurityException.class, () -> service.approve(12L, pending.id(), "same person", makerUnderAnotherId));

        PspDataSettingsService.ChangeView approved = service.approve(12L, pending.id(), "Agreed", checker);
        assertEquals("APPLIED", approved.status());
        assertEquals(10, psp.getRetentionYears());
        assertEquals(T0, psp.getRetentionEffectiveSince());
        verify(events, atLeastOnce()).publishEvent(any(PspDataSettingsChangedEvent.class));
        verify(audit).logAction(eq(maker), eq("PSP_DATA_SETTING_PROPOSED"), eq("PSP_DATA_SETTING"), eq("12"),
                any(), any(), any(), anyString());
        verify(audit).logAction(eq(checker), eq("PSP_DATA_SETTING_APPLIED"), eq("PSP_DATA_SETTING"), eq("12"),
                any(), any(), any(), anyString());
    }

    @Test
    void reductionTakesEffectNoSoonerThanThirtyDaysAfterApproval() {
        psp.setRetentionYears(10);
        PspDataSettingsService.ChangeView pending = service.proposeRetention(12L, 7, "Back to 7", maker);
        PspDataSettingsService.ChangeView approved = service.approve(12L, pending.id(), "Agreed", checker);

        assertEquals("APPROVED", approved.status());
        assertEquals(T0.plus(Duration.ofDays(30)), approved.effectiveAt());
        assertEquals(10, psp.getRetentionYears(), "old value stays in force");
        PspDataSettingsService.RetentionState state = service.retentionState(12L);
        assertEquals(10, state.years());
        assertNotNull(state.pending());
        assertEquals(7, state.pending().years());
        assertEquals(T0, state.pending().approvedAt());

        clock.set(T0.plus(Duration.ofDays(30)).minusSeconds(1));
        assertEquals(0, service.applyDueChanges());
        assertEquals(10, service.retentionState(12L).years());

        clock.set(T0.plus(Duration.ofDays(30)).plusSeconds(5));
        assertEquals(1, service.applyDueChanges());
        assertEquals(7, psp.getRetentionYears());
        assertEquals(T0.plus(Duration.ofDays(30)), psp.getRetentionEffectiveSince(),
                "in force from effectiveAt, not from the job run");
        assertNull(service.retentionState(12L).pending());
    }

    @Test
    void readsApplyADueReductionEvenBeforeTheJobRuns() {
        psp.setRetentionYears(8);
        PspDataSettingsService.ChangeView pending = service.proposeRetention(12L, 5, "Minimum", maker);
        service.approve(12L, pending.id(), "Agreed", checker);
        clock.set(T0.plus(Duration.ofDays(31)));
        assertEquals(5, service.view(12L).retentionYears());
    }

    @Test
    void proposingTheCurrentValueCancelsAWaitingReduction() {
        psp.setRetentionYears(10);
        service.approve(12L, service.proposeRetention(12L, 6, "Lower", maker).id(), "ok", checker);
        PspDataSettingsService.ChangeView cancel = service.proposeRetention(12L, 10, "Keep 10", maker);
        service.approve(12L, cancel.id(), "ok", checker);
        assertNull(service.retentionState(12L).pending());
        assertTrue(rows.stream().anyMatch(r -> r.getStatus() == Status.SUPERSEDED && "6".equals(r.getNewValue())));
        clock.set(T0.plus(Duration.ofDays(40)));
        assertEquals(10, service.view(12L).retentionYears());
    }

    @Test
    void retentionBoundsAndOnePendingRequestPerSetting() {
        assertThrows(IllegalArgumentException.class, () -> service.proposeRetention(12L, 4, "too low", maker));
        assertThrows(IllegalArgumentException.class, () -> service.proposeRetention(12L, 26, "too high", maker));
        assertThrows(IllegalArgumentException.class, () -> service.proposeRetention(12L, 5, "same", maker));
        assertThrows(IllegalArgumentException.class, () -> service.proposeRetention(12L, 7, " ", maker));
        service.proposeRetention(12L, 7, "first", maker);
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> service.proposeRetention(12L, 8, "second", maker));
        assertEquals(409, conflict.getStatusCode().value());
    }

    @Test
    void enablingSupportAccessNeedsApprovalWithdrawingIsImmediate() {
        PspDataSettingsService.ChangeView pending = service.proposeSupportAccess(12L, true, "Ticket 4411", maker);
        assertEquals("PENDING_APPROVAL", pending.status());
        assertFalse(psp.getSupportAccessEnabled());
        service.approve(12L, pending.id(), "ok", checker);
        assertTrue(psp.getSupportAccessEnabled());
        assertEquals(T0, psp.getSupportAccessChangedAt());

        clock.set(T0.plusSeconds(3600));
        PspDataSettingsService.ChangeView off = service.proposeSupportAccess(12L, false, "Ticket closed", maker);
        assertEquals("APPLIED", off.status());
        assertFalse(psp.getSupportAccessEnabled());
        assertEquals(T0.plusSeconds(3600), psp.getSupportAccessChangedAt());
    }

    @Test
    void withdrawingConsentSupersedesAPendingOptIn() {
        psp.setCrossPspOptIn(false);
        PspDataSettingsService.ChangeView pending = service.proposeCrossPspOptIn(12L, true, "Join the network", maker);
        psp.setCrossPspOptIn(true); // e.g. applied through another path; now withdraw
        service.proposeCrossPspOptIn(12L, false, "Leave", maker);
        assertEquals(Status.SUPERSEDED, rows.stream().filter(r -> r.getId().equals(pending.id())).findFirst()
                .orElseThrow().getStatus());
        assertFalse(psp.getCrossPspOptIn());
    }

    @Test
    void rejectedChangeNeverApplies() {
        PspDataSettingsService.ChangeView pending = service.proposeRetention(12L, 9, "Longer", maker);
        assertThrows(IllegalArgumentException.class, () -> service.reject(12L, pending.id(), "", checker));
        assertEquals("REJECTED", service.reject(12L, pending.id(), "Not needed", checker).status());
        assertEquals(5, psp.getRetentionYears());
        ResponseStatusException again = assertThrows(ResponseStatusException.class,
                () -> service.approve(12L, pending.id(), "late", checker));
        assertEquals(409, again.getStatusCode().value());
    }

    @Test
    void aChangeOfAnotherPspIsNotFound() {
        PspDataSettingsService.ChangeView pending = service.proposeRetention(12L, 9, "Longer", maker);
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> service.approve(13L, pending.id(), "ok", checker));
        assertEquals(404, missing.getStatusCode().value());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────

    private static User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        Role r = new Role();
        r.setName(role);
        user.setRole(r);
        return user;
    }

    /** A repository mock backed by {@link #rows}, answering the derived queries the service uses. */
    private PspDataSettingChangeRepository inMemoryChanges() {
        PspDataSettingChangeRepository repo = mock(PspDataSettingChangeRepository.class);
        when(repo.save(any(PspDataSettingChange.class))).thenAnswer(i -> {
            PspDataSettingChange c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId(ids.incrementAndGet());
                rows.add(c);
            }
            return c;
        });
        when(repo.findById(any())).thenAnswer(i -> rows.stream().filter(r -> r.getId().equals(i.getArgument(0))).findFirst());
        when(repo.findByPspIdOrderByRequestedAtDesc(any())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getPspId().equals(i.getArgument(0)))
                .sorted(Comparator.comparing(PspDataSettingChange::getRequestedAt).reversed()).toList());
        when(repo.findByPspIdAndStatusInOrderByRequestedAtAsc(any(), any())).thenAnswer(i -> {
            Collection<Status> statuses = i.getArgument(1);
            return rows.stream().filter(r -> r.getPspId().equals(i.getArgument(0)) && statuses.contains(r.getStatus()))
                    .toList();
        });
        when(repo.findFirstByPspIdAndSettingAndStatus(any(), any(), any())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getPspId().equals(i.getArgument(0)) && r.getSetting() == i.<Setting>getArgument(1)
                        && r.getStatus() == i.<Status>getArgument(2)).findFirst());
        when(repo.findByPspIdAndSettingAndStatus(any(), any(), any())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getPspId().equals(i.getArgument(0)) && r.getSetting() == i.<Setting>getArgument(1)
                        && r.getStatus() == i.<Status>getArgument(2)).toList());
        when(repo.findByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(any(), any())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getStatus() == i.<Status>getArgument(0) && r.getEffectiveAt() != null
                        && !r.getEffectiveAt().isAfter(i.<Instant>getArgument(1))).toList());
        when(repo.findByPspIdAndStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(any(), any(), any())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getPspId().equals(i.getArgument(0)) && r.getStatus() == i.<Status>getArgument(1)
                        && r.getEffectiveAt() != null && !r.getEffectiveAt().isAfter(i.<Instant>getArgument(2))).toList());
        return repo;
    }

    /** A clock the test can move. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
