package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.security.PspApiKey;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.security.PspApiKeyRepository;
import com.posgateway.aml.service.auth.OnboardingInviteService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PspApiKeyServiceTest {

    private static Psp psp(long id, String status) {
        Psp psp = new Psp();
        psp.setPspId(id);
        psp.setStatus(status);
        return psp;
    }

    private static PspApiKey row(String plaintext, Psp psp) {
        PspApiKey row = new PspApiKey();
        row.setKeyHash(OnboardingInviteService.sha256(plaintext));
        row.setKeyPrefix(plaintext.substring(0, 12));
        row.setPsp(psp);
        return row;
    }

    @Test
    void createsRandomKeyAndPersistsOnlySha256Hash() {
        PspApiKeyRepository repository = mock(PspApiKeyRepository.class);
        PspRepository psps = mock(PspRepository.class);
        when(psps.findById(12L)).thenReturn(Optional.of(psp(12L, "ACTIVE")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var issued = new PspApiKeyService(repository, psps).create(12L, 4L);

        assertTrue(issued.plaintextKey().startsWith("hka_"));
        verify(repository).save(argThat(row ->
                !issued.plaintextKey().equals(row.getKeyHash())
                        && row.getKeyHash().equals(OnboardingInviteService.sha256(issued.plaintextKey()))
                        && row.getExpiresAt() == null));
    }

    @Test
    void defaultTtlSetsExpiry() {
        PspApiKeyRepository repository = mock(PspApiKeyRepository.class);
        PspRepository psps = mock(PspRepository.class);
        when(psps.findById(12L)).thenReturn(Optional.of(psp(12L, "ACTIVE")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new PspApiKeyService(repository, psps, 90).create(12L, 4L);

        verify(repository).save(argThat(row -> row.getExpiresAt() != null
                && row.getExpiresAt().isAfter(LocalDateTime.now().plusDays(89))));
    }

    @Test
    void validKeyAuthenticatesToItsPsp() {
        String key = "hka_validvalidvalidvalidvalidvalidvalidval";
        PspApiKeyRepository repository = mock(PspApiKeyRepository.class);
        when(repository.findByKeyHashWithPsp(OnboardingInviteService.sha256(key)))
                .thenReturn(Optional.of(row(key, psp(7L, "ACTIVE"))));

        var auth = new PspApiKeyService(repository, mock(PspRepository.class)).authenticate(" " + key + " ");

        assertTrue(auth.isPresent());
        assertEquals(7L, auth.get().psp().getPspId());
        assertEquals(key.substring(0, 12), auth.get().keyPrefix());
    }

    @Test
    void revokedExpiredInactiveUnknownAndMalformedKeysAreRejected() {
        String key = "hka_validvalidvalidvalidvalidvalidvalidval";
        PspApiKeyRepository repository = mock(PspApiKeyRepository.class);
        PspApiKeyService service = new PspApiKeyService(repository, mock(PspRepository.class));
        String hash = OnboardingInviteService.sha256(key);

        PspApiKey revoked = row(key, psp(7L, "ACTIVE"));
        revoked.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByKeyHashWithPsp(hash)).thenReturn(Optional.of(revoked));
        assertTrue(service.authenticate(key).isEmpty(), "revoked");

        PspApiKey expired = row(key, psp(7L, "ACTIVE"));
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(repository.findByKeyHashWithPsp(hash)).thenReturn(Optional.of(expired));
        assertTrue(service.authenticate(key).isEmpty(), "expired");

        when(repository.findByKeyHashWithPsp(hash)).thenReturn(Optional.of(row(key, psp(7L, "SUSPENDED"))));
        assertTrue(service.authenticate(key).isEmpty(), "inactive PSP");

        when(repository.findByKeyHashWithPsp(hash)).thenReturn(Optional.empty());
        assertTrue(service.authenticate(key).isEmpty(), "unknown");

        reset(repository);
        assertTrue(service.authenticate("eyJhbGciOiJIUzI1NiJ9.x.y").isEmpty(), "not an hka_ key");
        assertTrue(service.authenticate("hka_" + "x".repeat(200)).isEmpty(), "oversized");
        assertTrue(service.authenticate(null).isEmpty(), "missing");
        verify(repository, never()).findByKeyHashWithPsp(anyString());
    }
}
