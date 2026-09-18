package com.posgateway.aml.service.auth;

import com.posgateway.aml.entity.auth.OnboardingInvite;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.auth.OnboardingInviteRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnboardingInviteServiceTest {
    @Test
    void storesOnlyTokenHashAndReturnsPlaintextOnce() {
        OnboardingInviteRepository repository = mock(OnboardingInviteRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OnboardingInviteService service = new OnboardingInviteService(repository, mock(PspRepository.class));

        var issued = service.issue(null, "VIEWER", LocalDateTime.now().plusDays(1), 7L);

        assertNotNull(issued.token());
        verify(repository).save(argThat(invite ->
                !issued.token().equals(invite.getTokenHash())
                        && invite.getTokenHash().equals(OnboardingInviteService.sha256(issued.token()))));
    }
}
