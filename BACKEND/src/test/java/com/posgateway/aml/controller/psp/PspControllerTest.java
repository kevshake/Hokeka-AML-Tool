package com.posgateway.aml.controller.psp;

import com.posgateway.aml.dto.psp.PspCbkConfigRequest;
import com.posgateway.aml.dto.psp.PspResponse;
import com.posgateway.aml.dto.psp.PspUpdateRequest;
import com.posgateway.aml.dto.psp.PspUserCreationRequest;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.integration.cbk.CbkProperties;
import com.posgateway.aml.mapper.PspMapper;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.psp.PspService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PspControllerTest {

    @Mock private PspService pspService;
    @Mock private PspMapper pspMapper;
    @Mock private PspRepository pspRepository;
    @Mock private CbkProperties cbkProperties;

    private PspController controller;

    @BeforeEach
    void setUp() {
        controller = new PspController(pspService, pspMapper, pspRepository, cbkProperties);
    }

    @Test
    void tenantCannotReadOrMutateAnotherPsp() {
        User tenant = tenantUser(1L);
        PspUserCreationRequest createUser = new PspUserCreationRequest();
        createUser.setPspId(2L);

        assertEquals(403, controller.getPsp(2L, tenant).getStatusCode().value());
        assertEquals(403, controller.updatePspProfile(2L, new PspUpdateRequest(), tenant).getStatusCode().value());
        assertEquals(403, controller.getCbkConfig(2L, tenant).getStatusCode().value());
        assertEquals(403, controller.createPspUser(createUser, tenant).getStatusCode().value());
        verifyNoInteractions(pspRepository, pspService);
    }

    @Test
    void selfEndpointReturnsAuthenticatedOrganization() {
        User tenant = tenantUser(7L);
        PspResponse response = new PspResponse();
        response.setId(7L);
        when(pspMapper.toResponse(tenant.getPsp())).thenReturn(response);

        var result = controller.getMyPsp(tenant);

        assertEquals(200, result.getStatusCode().value());
        assertEquals(7L, result.getBody().getId());
    }

    @Test
    void tenantCbkUpdateCannotPromoteItselfToLive() {
        User tenant = tenantUser(4L);
        Psp psp = tenant.getPsp();
        psp.setCbkEnvironment("preprod");
        psp.setCbkAllowLive(false);
        when(pspRepository.findById(4L)).thenReturn(Optional.of(psp));
        when(pspRepository.save(any(Psp.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PspCbkConfigRequest request = new PspCbkConfigRequest();
        request.setCbkInstitutionCode("KE-PSP-4");
        request.setCbkEnvironment("live");
        request.setCbkAllowLive(true);

        var result = controller.updateCbkConfig(4L, request, tenant);

        assertEquals(200, result.getStatusCode().value());
        ArgumentCaptor<Psp> saved = ArgumentCaptor.forClass(Psp.class);
        verify(pspRepository).save(saved.capture());
        assertEquals("KE-PSP-4", saved.getValue().getCbkInstitutionCode());
        assertEquals("preprod", saved.getValue().getCbkEnvironment());
        assertFalse(saved.getValue().getCbkAllowLive());
    }

    private static User tenantUser(Long pspId) {
        Psp psp = new Psp();
        psp.setPspId(pspId);
        return User.builder().psp(psp).build();
    }
}
