package com.posgateway.aml.service.onprem;

import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import com.posgateway.aml.dto.onprem.OnPremLeaseRequest;
import com.posgateway.aml.dto.onprem.OnPremLeaseResponse;
import com.posgateway.aml.entity.onprem.OnPremInstance;
import com.posgateway.aml.entity.onprem.OnPremInstanceStatus;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.onprem.OnPremInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnPremLeaseServiceTest {

    @Mock private OnPremInstanceRepository repository;
    @Mock private PspRepository pspRepository;
    @Mock private OnPremLeaseTokenService tokenService;
    @Mock private OnPremCheckInScheduler checkInScheduler;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private OnPremLeaseService service;

    @BeforeEach
    void setUp() {
        HokekaAuthProperties props = new HokekaAuthProperties();
        props.setCheckIntervalDays(1);
        service = new OnPremLeaseService(
                repository, pspRepository, passwordEncoder, tokenService, checkInScheduler, props);
    }

    @Test
    void grantsLeaseForValidCredentials() {
        OnPremInstance entity = activeInstance("secret-plain");
        Psp psp = new Psp();
        psp.setPspId(9L);
        psp.setStatus("ACTIVE");

        when(repository.findByClientId("op_abc")).thenReturn(Optional.of(entity));
        when(pspRepository.findById(9L)).thenReturn(Optional.of(psp));
        Instant nextCheck = Instant.parse("2026-07-24T03:15:00Z");
        when(checkInScheduler.assignNextCheckAt(any(), any(), any(Integer.class), any()))
                .thenReturn(nextCheck);
        when(tokenService.issueToken(any(), any(), any(), any(), any(Integer.class), any()))
                .thenReturn("payload.sig");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OnPremLeaseResponse response = service.authenticateAndGrant(new OnPremLeaseRequest(
                "op_abc", "secret-plain", "inst-1", "host", "1.0"));

        assertEquals("inst-1", response.instanceId());
        assertEquals(9L, response.pspId());
        assertEquals(nextCheck, response.nextCheckAt());
        assertEquals(7, response.approvedDays());
        assertEquals("payload.sig", response.leaseToken());
        assertNotNull(response.validUntil());
        assertTrue(response.validUntil().isAfter(Instant.now()));

        ArgumentCaptor<OnPremInstance> captor = ArgumentCaptor.forClass(OnPremInstance.class);
        verify(repository).save(captor.capture());
        assertEquals(nextCheck, captor.getValue().getNextCheckAt());
    }

    @Test
    void rejectsBadSecret() {
        OnPremInstance entity = activeInstance("correct");
        when(repository.findByClientId("op_abc")).thenReturn(Optional.of(entity));

        assertThrows(ResponseStatusException.class, () ->
                service.authenticateAndGrant(new OnPremLeaseRequest(
                        "op_abc", "wrong", "inst-1", null, null)));
    }

    @Test
    void rejectsRevokedInstance() {
        OnPremInstance entity = activeInstance("secret-plain");
        entity.setStatus(OnPremInstanceStatus.REVOKED);
        when(repository.findByClientId("op_abc")).thenReturn(Optional.of(entity));

        assertThrows(ResponseStatusException.class, () ->
                service.authenticateAndGrant(new OnPremLeaseRequest(
                        "op_abc", "secret-plain", "inst-1", null, null)));
    }

    private OnPremInstance activeInstance(String plainSecret) {
        OnPremInstance entity = new OnPremInstance();
        entity.setId(1L);
        entity.setPspId(9L);
        entity.setInstanceId("inst-1");
        entity.setClientId("op_abc");
        entity.setClientSecretHash(passwordEncoder.encode(plainSecret));
        entity.setDisplayName("Node");
        entity.setStatus(OnPremInstanceStatus.ACTIVE);
        entity.setApprovedDays(7);
        return entity;
    }
}
