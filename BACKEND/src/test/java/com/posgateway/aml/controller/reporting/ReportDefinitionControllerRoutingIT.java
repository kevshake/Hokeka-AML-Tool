package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.repository.reporting.ReportDefinitionRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for {@link ReportDefinitionController} routing fix.
 *
 * <p>Was {@code @RequestMapping("/api/reports/definitions")} which doubled with
 * the {@code /api/v1} context-path. Fixed to {@code "/reports/definitions"}.
 */
@WebMvcTest(controllers = ReportDefinitionController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ReportDefinitionControllerRoutingIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportRepository reportRepository;

    @MockBean
    private ReportDefinitionRepository reportDefinitionRepository;

    @MockBean
    private PspIsolationService pspIsolationService;

    @Test
    void listReportDefinitions_isReachable() throws Exception {
        Page empty = new PageImpl<>(List.of());
        when(reportRepository.findByFilters(any(), any(), any(), any())).thenReturn(empty);

        mockMvc.perform(get("/reports/definitions"))
                .andExpect(status().isOk());
    }

    @Test
    void listCategories_isReachable() throws Exception {
        mockMvc.perform(get("/reports/definitions/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void doubledUpLegacyPath_returns404() throws Exception {
        mockMvc.perform(get("/api/reports/definitions"))
                .andExpect(status().isNotFound());
    }
}
