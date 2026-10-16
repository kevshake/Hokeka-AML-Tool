package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.security.PaymentBlacklistEntry;
import com.posgateway.aml.repository.security.PaymentBlacklistRepository;
import com.posgateway.aml.service.cache.FeatureCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers Do-Not-Honour TTL behaviour and tenant isolation (WP-00).
 */
@ExtendWith(MockitoExtension.class)
class PaymentBlacklistServiceTest {

    @Mock
    private FeatureCacheService featureCacheService;
    @Mock
    private PaymentBlacklistRepository blacklistRepository;
    @InjectMocks
    private PaymentBlacklistService service;

    @Test
    void doNotHonourBlock_isSavedWithExpiryAndNotCached() {
        when(blacklistRepository.findTenantActive(7L, "pan", "hash"))
                .thenReturn(Optional.empty());
        LocalDateTime expiry = LocalDateTime.now().plusDays(30);

        service.addToBlacklist(7L, "pan", "hash", "Do Not Honour", null, expiry);

        ArgumentCaptor<PaymentBlacklistEntry> captor = ArgumentCaptor.forClass(PaymentBlacklistEntry.class);
        verify(blacklistRepository).save(captor.capture());
        assertEquals(expiry, captor.getValue().getExpiresAt());
        assertEquals(7L, captor.getValue().getPspId());
        assertFalse(Boolean.TRUE.equals(captor.getValue().getPlatformWide()));
        verify(featureCacheService, never()).addToBlacklist(any(), any(), any());
    }

    /** Verifies permanent block is mirrored into tenant cache. */
    @Test
    void permanentBlock_isMirroredIntoTenantCache() {
        when(blacklistRepository.findTenantActive(7L, "pan", "hash"))
                .thenReturn(Optional.empty());

        service.addToBlacklist(7L, "pan", "hash", "Confirmed fraud", null, null);

        ArgumentCaptor<PaymentBlacklistEntry> captor = ArgumentCaptor.forClass(PaymentBlacklistEntry.class);
        verify(blacklistRepository).save(captor.capture());
        assertNull(captor.getValue().getExpiresAt());
        verify(featureCacheService).addToBlacklist(7L, "pan", "hash");
    }

    /** Verifies psp ablock does not hit psp b via cache miss and db scope. */
    @Test
    void pspABlock_doesNotHitPspB_viaCacheMissAndDbScope() {
        when(featureCacheService.isBlacklisted(eq(2L), eq("pan"), eq("hash"))).thenReturn(false);
        when(featureCacheService.isBlacklisted(isNull(), eq("pan"), eq("hash"))).thenReturn(false);
        when(blacklistRepository.existsActiveNonExpiredForTenantOrPlatform(
                eq(2L), eq("pan"), eq("hash"), any(LocalDateTime.class))).thenReturn(false);

        assertFalse(service.isBlacklisted(2L, "pan", "hash"));
    }

    /** Verifies platform wide hit applies to any psp. */
    @Test
    void platformWideHit_appliesToAnyPsp() {
        when(featureCacheService.isBlacklisted(eq(2L), eq("pan"), eq("hash"))).thenReturn(false);
        when(featureCacheService.isBlacklisted(isNull(), eq("pan"), eq("hash"))).thenReturn(true);

        assertTrue(service.isBlacklisted(2L, "pan", "hash"));
    }

    @Test
    void repeatDoNotHonour_extendsWindow_neverShortens() {
        PaymentBlacklistEntry existing = new PaymentBlacklistEntry();
        existing.setPspId(7L);
        existing.setExpiresAt(LocalDateTime.now().plusDays(5));
        when(blacklistRepository.findTenantActive(7L, "pan", "hash"))
                .thenReturn(Optional.of(existing));

        LocalDateTime later = LocalDateTime.now().plusDays(30);
        service.addToBlacklist(7L, "pan", "hash", "Do Not Honour again", null, later);
        assertEquals(later, existing.getExpiresAt());

        LocalDateTime earlier = LocalDateTime.now().plusDays(2);
        service.addToBlacklist(7L, "pan", "hash", "Do Not Honour earlier", null, earlier);
        assertEquals(later, existing.getExpiresAt());
    }
}
