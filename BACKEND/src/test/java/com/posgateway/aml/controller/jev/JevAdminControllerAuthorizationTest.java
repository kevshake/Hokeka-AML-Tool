package com.posgateway.aml.controller.jev;

import com.posgateway.aml.entity.jev.JevEngineSetting;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.jev.JevAuditAccessService;
import com.posgateway.aml.service.jev.JevEngineConfigService;
import com.posgateway.aml.service.jev.JevOperatorAuthorization;
import com.posgateway.aml.service.jev.JevStatusService;
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
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JevAdminController.class, properties = {
        "spring.security.enabled=false",
        "spring.data.jpa.repositories.enabled=false"
})
@ContextConfiguration(classes = {
        JevAdminController.class,
        JevAdminControllerAuthorizationTest.MethodSecurityConfig.class
})
@Import(JevOperatorAuthorization.class)
class JevAdminControllerAuthorizationTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mvc;
    @MockBean JevStatusService statusService;
    @MockBean JevEngineConfigService engineConfigService;
    @MockBean JevAuditAccessService auditAccessService;
    @MockBean PspRepository pspRepository;
    @MockBean PspIsolationService pspIsolationService;

    @BeforeEach
    void stubs() {
        when(statusService.status()).thenReturn(Map.of("configured", true, "model", "test/model"));
        when(engineConfigService.listAll()).thenReturn(List.of(new JevEngineSetting()));
        when(auditAccessService.forAlert(1L)).thenReturn(List.of(Map.of("id", 1)));
        Psp psp = new Psp();
        psp.setPspId(7L);
        psp.setAiInlineMode(false);
        psp.setAiInlineBudgetMs(500);
        when(pspRepository.findById(7L)).thenReturn(Optional.of(psp));
    }

    @Test
    @WithMockUser(authorities = "ROLE_PSP_ADMIN")
    void pspAdminForbiddenOnOperatorStatusEndpoint() throws Exception {
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(false);
        mvc.perform(get("/jev/status")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PSP_ADMIN")
    void pspAdminForbiddenOnEngineList() throws Exception {
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(false);
        mvc.perform(get("/jev/engines")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PSP_ADMIN")
    void pspAdminForbiddenOnPspInlineSettings() throws Exception {
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(false);
        mvc.perform(get("/jev/psps/7/ai-settings")).andExpect(status().isForbidden());
        mvc.perform(put("/jev/psps/7/ai-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiInlineMode\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void platformAdminAllowedOnOperatorEndpoints() throws Exception {
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(true);
        mvc.perform(get("/jev/status")).andExpect(status().isOk());
        mvc.perform(get("/jev/engines")).andExpect(status().isOk());
        mvc.perform(get("/jev/psps/7/ai-settings")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PSP_ADMIN")
    void pspAdminMayReadTenantScopedAudit() throws Exception {
        when(pspIsolationService.isPlatformAdministrator()).thenReturn(false);
        mvc.perform(get("/jev/audit/alert/1")).andExpect(status().isOk());
    }
}
