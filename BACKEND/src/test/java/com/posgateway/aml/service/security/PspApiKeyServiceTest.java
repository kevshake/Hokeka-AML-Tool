package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.security.PspApiKey;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.security.PspApiKeyRepository;
import com.posgateway.aml.service.auth.OnboardingInviteService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PspApiKeyServiceTest {
    @Test
    void createsRandomKeyAndPersistsOnlySha256Hash() {
        PspApiKeyRepository repository = mock(PspApiKeyRepository.class);
        PspRepository psps = mock(PspRepository.class);
        Psp psp = new Psp();
        psp.setPspId(12L);
        when(psps.findById(12L)).thenReturn(Optional.of(psp));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var issued = new PspApiKeyService(repository, psps).create(12L, 4L);

        assertTrue(issued.plaintextKey().startsWith("hka_"));
        verify(repository).save(argThat(row ->
                !issued.plaintextKey().equals(row.getKeyHash())
                        && row.getKeyHash().equals(OnboardingInviteService.sha256(issued.plaintextKey()))));
    }
}
