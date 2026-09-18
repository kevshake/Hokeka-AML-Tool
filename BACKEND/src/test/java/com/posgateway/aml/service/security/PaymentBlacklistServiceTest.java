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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Do-Not-Honour time-boxed blacklist behaviour: a decline registers the card for a fixed
 * window, the window can be extended but never shortened, permanent blocks stay permanent, and only
 * permanent blocks are mirrored into the fast cache (time-boxed ones must expire via the DB query).
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
        when(blacklistRepository.findByEntryTypeAndEntryValueAndActiveTrue("pan", "hash"))
                .thenReturn(Optional.empty());
        LocalDateTime expiry = LocalDateTime.now().plusDays(30);

        service.addToBlacklist("pan", "hash", "Do Not Honour", null, expiry);

        ArgumentCaptor<PaymentBlacklistEntry> captor = ArgumentCaptor.forClass(PaymentBlacklistEntry.class);
        verify(blacklistRepository).save(captor.capture());
        assertEquals(expiry, captor.getValue().getExpiresAt());
        // Time-boxed blocks must NOT enter the permanent cache, or they'd never expire.
        verify(featureCacheService, never()).addToBlacklist(any(), any());
    }

    @Test
    void permanentBlock_isMirroredIntoCache() {
        when(blacklistRepository.findByEntryTypeAndEntryValueAndActiveTrue("pan", "hash"))
                .thenReturn(Optional.empty());

        service.addToBlacklist("pan", "hash", "Confirmed fraud", null, null);

        ArgumentCaptor<PaymentBlacklistEntry> captor = ArgumentCaptor.forClass(PaymentBlacklistEntry.class);
        verify(blacklistRepository).save(captor.capture());
        assertNull(captor.getValue().getExpiresAt());
        verify(featureCacheService).addToBlacklist("pan", "hash");
    }

    @Test
    void repeatDoNotHonour_extendsWindow_neverShortens() {
        PaymentBlacklistEntry existing = new PaymentBlacklistEntry();
        existing.setExpiresAt(LocalDateTime.now().plusDays(5));
        when(blacklistRepository.findByEntryTypeAndEntryValueAndActiveTrue("pan", "hash"))
                .thenReturn(Optional.of(existing));

        LocalDateTime later = LocalDateTime.now().plusDays(30);
        service.addToBlacklist("pan", "hash", "Do Not Honour again", null, later);
        assertEquals(later, existing.getExpiresAt());

        // A shorter window must not pull the expiry back in.
        LocalDateTime earlier = LocalDateTime.now().plusDays(2);
        service.addToBlacklist("pan", "hash", "Do Not Honour", null, earlier);
        assertEquals(later, existing.getExpiresAt());
    }

    @Test
    void permanentBlock_isNotDowngradedByATimeBoxedOne() {
        PaymentBlacklistEntry permanent = new PaymentBlacklistEntry();
        permanent.setExpiresAt(null);
        when(blacklistRepository.findByEntryTypeAndEntryValueAndActiveTrue("pan", "hash"))
                .thenReturn(Optional.of(permanent));

        service.addToBlacklist("pan", "hash", "Do Not Honour", null, LocalDateTime.now().plusDays(30));
        assertNull(permanent.getExpiresAt());
    }

    @Test
    void isBlacklisted_usesExpiryAwareQuery() {
        when(featureCacheService.isBlacklisted("pan", "hash")).thenReturn(false);
        when(blacklistRepository.existsActiveNonExpired(eq("pan"), eq("hash"), any()))
                .thenReturn(true);

        assertTrue(service.isBlacklisted("pan", "hash"));
        verify(blacklistRepository).existsActiveNonExpired(eq("pan"), eq("hash"), any());
    }
}
