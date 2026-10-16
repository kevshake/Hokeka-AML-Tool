package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.security.PaymentBlacklistEntry;
import com.posgateway.aml.repository.security.PaymentBlacklistRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.cache.FeatureCacheService;
import com.posgateway.aml.service.security.PaymentBlacklistAdminService.CreateRequest;
import com.posgateway.aml.service.security.PaymentBlacklistAdminService.UpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Payment blacklist CRUD: V250 tenancy, platform-wide list for admins only, audit, cache, PAN hashing. */
class PaymentBlacklistAdminServiceTest {

    private final PaymentBlacklistRepository repo = mock(PaymentBlacklistRepository.class);
    private final FeatureCacheService cache = mock(FeatureCacheService.class);
    private final PspIsolationService isolation = mock(PspIsolationService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final PiiLookupHasher hasher = new PiiLookupHasher("k".repeat(32));
    private PaymentBlacklistAdminService service;

    private static User user(String role, long pspId) {
        Role r = new Role();
        r.setName(role);
        Psp psp = new Psp();
        psp.setPspId(pspId);
        return User.builder().id(4L).username(role).email(role + "@x").passwordHash("").role(r).psp(psp).build();
    }

    private void callerIs(User u, boolean platformAdmin) {
        when(isolation.getCurrentUser()).thenReturn(u);
        when(isolation.isPlatformAdministrator(any(User.class))).thenReturn(platformAdmin);
    }

    private static PaymentBlacklistEntry entry(Long id, Long pspId, boolean platformWide, boolean active) {
        PaymentBlacklistEntry e = new PaymentBlacklistEntry();
        e.setId(id);
        e.setPspId(pspId);
        e.setPlatformWide(platformWide);
        e.setEntryType("ip");
        e.setEntryValue("10.0.0.1");
        e.setActive(active);
        return e;
    }

    @BeforeEach
    void setUp() {
        service = new PaymentBlacklistAdminService(repo, cache, isolation, hasher, audit);
        when(repo.save(any())).thenAnswer(inv -> {
            PaymentBlacklistEntry e = inv.getArgument(0);
            if (e.getId() == null) e.setId(99L);
            return e;
        });
        when(repo.findTenantAny(any(), anyString(), anyString())).thenReturn(Optional.empty());
        when(repo.findPlatformAny(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void pspUserCreatesForOwnPspWithHashedPanAuditAndCache() {
        callerIs(user("PSP_ADMIN", 7L), false);

        PaymentBlacklistEntry saved = service.create(
                new CreateRequest("PAN", "4111 1111 1111 1111", "fraud", null, null, null), "1.2.3.4");

        assertThat(saved.getPspId()).isEqualTo(7L);
        assertThat(saved.getPlatformWide()).isFalse();
        assertThat(saved.getEntryValue()).isEqualTo(hasher.hashPan("4111111111111111")).doesNotContain("4111");
        verify(cache).addToBlacklist(7L, "pan", saved.getEntryValue());
        verify(audit).logAction(any(), eq("BLACKLIST_ADD"), eq("PAYMENT_BLACKLIST"), eq("99"), isNull(), any(),
                eq("1.2.3.4"), eq("fraud"));
    }

    @Test
    void pspUserCannotWritePlatformWideOrOtherPsp() {
        callerIs(user("PSP_ADMIN", 7L), false);
        assertThatThrownBy(() -> service.create(new CreateRequest("ip", "1.1.1.1", "r", null, null, true), null))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.create(new CreateRequest("ip", "1.1.1.1", "r", null, 8L, null), null))
                .isInstanceOf(SecurityException.class);

        when(repo.findById(1L)).thenReturn(Optional.of(entry(1L, null, true, true)));
        when(repo.findById(2L)).thenReturn(Optional.of(entry(2L, 8L, false, true)));
        assertThatThrownBy(() -> service.remove(1L, "x", null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.remove(2L, "x", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.get(2L)).hasMessageContaining("not found");
        assertThat(service.get(1L).getPlatformWide()).isTrue(); // platform list is readable
    }

    @Test
    void platformAdminManagesPlatformWideList() {
        callerIs(user("SUPER_ADMIN", 0L), true);
        PaymentBlacklistEntry saved = service.create(
                new CreateRequest("terminal", "T-1", "compromised", null, null, true), null);
        assertThat(saved.getPspId()).isNull();
        assertThat(saved.getPlatformWide()).isTrue();
        verify(cache).addToBlacklist(null, "terminal", "T-1");

        assertThatThrownBy(() -> service.create(new CreateRequest("ip", "1.1.1.1", "r", null, null, null), null))
                .hasMessageContaining("pspId is required");
    }

    @Test
    void duplicatesConflictRemovedEntriesReactivate() {
        callerIs(user("PSP_ADMIN", 7L), false);
        when(repo.findTenantAny(7L, "ip", "10.0.0.1")).thenReturn(Optional.of(entry(5L, 7L, false, true)));
        assertThatThrownBy(() -> service.create(new CreateRequest("ip", "10.0.0.1", "r", null, null, null), null))
                .isInstanceOf(IllegalStateException.class);

        PaymentBlacklistEntry removed = entry(5L, 7L, false, false);
        when(repo.findTenantAny(7L, "ip", "10.0.0.1")).thenReturn(Optional.of(removed));
        PaymentBlacklistEntry back = service.create(new CreateRequest("ip", "10.0.0.1", "again", null, null, null), null);
        assertThat(back.getId()).isEqualTo(5L);
        assertThat(back.getActive()).isTrue();
        verify(audit).logAction(any(), eq("BLACKLIST_REACTIVATE"), any(), eq("5"), any(), any(), any(), eq("again"));
    }

    @Test
    void updateAndSoftDeleteKeepCacheInStep() {
        callerIs(user("PSP_ADMIN", 7L), false);
        PaymentBlacklistEntry e = entry(3L, 7L, false, true);
        when(repo.findById(3L)).thenReturn(Optional.of(e));

        service.update(3L, new UpdateRequest("timed", LocalDateTime.now().plusDays(30), null, null), null);
        verify(cache).removeFromBlacklist(7L, "ip", "10.0.0.1"); // expiring entries are not cached

        service.remove(3L, "cleared", null);
        assertThat(e.getActive()).isFalse();
        verify(audit).logAction(any(), eq("BLACKLIST_REMOVE"), any(), eq("3"), any(), any(), any(), eq("cleared"));
    }

    @Test
    void invalidInputIsRejected() {
        callerIs(user("PSP_ADMIN", 7L), false);
        assertThatThrownBy(() -> service.create(new CreateRequest("email", "x", "r", null, null, null), null))
                .hasMessageContaining("entryType");
        assertThatThrownBy(() -> service.create(new CreateRequest("pan", "12ab", "r", null, null, null), null))
                .hasMessageContaining("PAN");
        assertThatThrownBy(() -> service.create(new CreateRequest("ip", "1.1.1.1", "r",
                LocalDateTime.now().minusDays(1), null, null), null)).hasMessageContaining("future");
        verify(repo, never()).save(any());
    }
}
