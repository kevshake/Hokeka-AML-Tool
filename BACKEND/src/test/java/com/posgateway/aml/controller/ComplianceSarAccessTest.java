package com.posgateway.aml.controller;

import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.compliance.ComplianceReportingService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tipping-off control: PSP operators and PSP admins cannot read the SAR register. */
@WebMvcTest(controllers = ComplianceReportingController.class, properties = {
        "spring.security.enabled=false",
        "spring.data.jpa.repositories.enabled=false"
})
@ContextConfiguration(classes = {ComplianceReportingController.class, ComplianceSarAccessTest.MethodSecurity.class})
class ComplianceSarAccessTest {

    /** Method security with the application's role hierarchy (PLATFORM_ADMIN > ADMIN > COMPLIANCE_OFFICER). */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
        @Bean
        RoleHierarchy roleHierarchy() {
            RoleHierarchyImpl h = new RoleHierarchyImpl();
            h.setHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN\nROLE_PLATFORM_ADMIN > ROLE_ADMIN\n"
                    + "ROLE_ADMIN > ROLE_COMPLIANCE_OFFICER\nROLE_ADMIN > ROLE_PSP_ADMIN\nROLE_PSP_ADMIN > ROLE_PSP_USER");
            return h;
        }
    }

    @Autowired MockMvc mvc;
    @MockBean ComplianceReportingService reportingService;
    @MockBean SuspiciousActivityReportRepository sarRepository;
    @MockBean PspIsolationService pspIsolationService;

    @Test
    @WithMockUser(roles = "PSP_USER")
    void pspUserIsForbidden() throws Exception {
        mvc.perform(get("/compliance/sar")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PSP_ADMIN")
    void pspAdminIsForbidden() throws Exception {
        mvc.perform(get("/compliance/sar")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COMPLIANCE_OFFICER")
    void complianceOfficerCanRead() throws Exception {
        when(reportingService.getAllSars(any())).thenReturn(List.of());
        mvc.perform(get("/compliance/sar")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MLRO")
    void mlroCanRead() throws Exception {
        when(reportingService.getAllSars(any())).thenReturn(List.of());
        mvc.perform(get("/compliance/sar")).andExpect(status().isOk());
    }
}
