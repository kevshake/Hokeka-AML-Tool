package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.service.reporting.ReportGenerationService;
import com.posgateway.aml.service.reporting.ReportHistoryService;
import com.posgateway.aml.service.reporting.ReportSchedulingService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the report-controller routing fix.
 *
 * <p>Bug history: {@link ReportController} originally declared
 * {@code @RequestMapping("/api/reports")}. Combined with the application's
 * context-path of {@code /api/v1}, every request had to hit
 * {@code /api/v1/api/reports/...} — the UI calls just {@code /api/v1/reports/...}
 * so all report endpoints 404'd. Fix changed the mapping to {@code "/reports"}.
 *
 * <p>Test strategy: {@link MockMvc} does not apply the servlet context-path,
 * so we hit {@code /reports/...} directly and verify the route resolves to a
 * non-404 status.
 */
@WebMvcTest(controllers = ReportController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ReportControllerRoutingIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportGenerationService reportGenerationService;

    @MockBean
    private ReportHistoryService reportHistoryService;

    @MockBean
    private ReportSchedulingService reportSchedulingService;

    @MockBean
    private PspIsolationService pspIsolationService;

    @Test
    void getScheduledReports_isReachable_notFourOhFour() throws Exception {
        when(pspIsolationService.sanitizePspId(any())).thenReturn(0L);
        Page<?> empty = new PageImpl<>(List.of());
        when(reportSchedulingService.getScheduledReports(any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn((Page) empty);

        // The actual mapping post-fix is /reports/schedule (was /api/reports/schedule).
        mockMvc.perform(get("/reports/schedule"))
                .andExpect(status().isOk());
    }

    @Test
    void getReportHistory_isReachable_notFourOhFour() throws Exception {
        when(pspIsolationService.sanitizePspId(any())).thenReturn(0L);
        Page<?> empty = new PageImpl<>(List.of());
        when(reportHistoryService.getReportHistory(any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn((Page) empty);

        mockMvc.perform(get("/reports/history"))
                .andExpect(status().isOk());
    }

    @Test
    void doubledUpLegacyPath_returns404() throws Exception {
        // After the fix, /api/reports/history must NOT resolve — proves the doubled-up
        // path that caused the original bug is gone.
        mockMvc.perform(get("/api/reports/history"))
                .andExpect(status().isNotFound());
    }
}
