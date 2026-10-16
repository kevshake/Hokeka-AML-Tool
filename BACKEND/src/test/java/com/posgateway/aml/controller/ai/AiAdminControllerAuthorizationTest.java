package com.posgateway.aml.controller.ai;

import com.posgateway.aml.entity.ai.AiEngineSetting;
import com.posgateway.aml.service.ai.decision.AiAuditAccessService;
import com.posgateway.aml.service.ai.decision.AiEngineConfigService;
import com.posgateway.aml.service.ai.decision.AiOperatorAuthorization;
import com.posgateway.aml.service.ai.decision.AiStatusService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiAdminController.class, properties = {
        "spring.security.enabled=false",
        "spring.data.jpa.repositories.enabled=false"
})
@ContextConfiguration(classes = {
        AiAdminController.class,
        AiAdminControllerAuthorizationTest.MethodSecurityConfig.class
})
@Import(AiOperatorAuthorization.class)
class AiAdminControllerAuthorizationTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mvc;
    @MockBean AiStatusService statusService;
    @MockBean AiEngineConfigService engineConfigService;
    @MockBean AiAuditAccessService auditAccessService;
    @MockBean PspIsolationService pspIsolationService;

    @BeforeEach
    void stubs() {
        when(statusService.status()).thenReturn(Map.of("configured", true, "model", "test/model"));
        when(engineConfigService.listAll()).thenReturn(List.of(new AiEngineSetting()));
        when(auditAccessService.forAlert(1L)).thenReturn(List.of(Map.of("id", 1)));
    }

    @Test
    @WithMockUser(authorities = "ROLE_PSP_ADMIN")
    void pspAdminForbiddenOnOperatorStatusEndpoint() throws Exception {
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(false);
        mvc.perform(get("/ai/status")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PSP_ADMIN")
    void pspAdminForbiddenOnEngineList() throws Exception {
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(false);
        mvc.perform(get("/ai/engines")).andExpect(status().isForbidden());
    }

    /**
     * R10c: the per-PSP "Edge waits for AI inline" settings only served the removed Edge feature-bag
     * POST, so the endpoints are gone, even for a platform operator.
     */
    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void pspInlineAiSettingsEndpointsAreGone() throws Exception {
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(true);
        mvc.perform(get("/ai/psps/7/ai-settings")).andExpect(status().isNotFound());
        mvc.perform(put("/ai/psps/7/ai-settings")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiInlineMode\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void platformAdminAllowedOnOperatorEndpoints() throws Exception {
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(true);
        mvc.perform(get("/ai/status")).andExpect(status().isOk());
        mvc.perform(get("/ai/engines")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PSP_ADMIN")
    void pspAdminMayReadTenantScopedAudit() throws Exception {
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(false);
        mvc.perform(get("/ai/audit/alert/1")).andExpect(status().isOk());
    }
}
