package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.service.reporting.RegulatoryReportingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for {@link RegulatoryReportingController} routing fix.
 *
 * <p>Was at {@code /regulatory/ctr} (lower-case, mixed style). The controller
 * is now at {@code @RequestMapping("/reporting/regulatory")} so under context
 * {@code /api/v1} it serves {@code /api/v1/reporting/regulatory/ctr}.
 */
@WebMvcTest(controllers = RegulatoryReportingController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RegulatoryReportingControllerRoutingIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegulatoryReportingService reportingService;

    @Test
    void generateCtr_isReachable_notFourOhFour() throws Exception {
        when(reportingService.generateCtr(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new RegulatoryReportingService.CurrencyTransactionReport());

        // The mapping is lower-case "ctr" — assert the canonical lowercase form is reachable.
        mockMvc.perform(get("/reporting/regulatory/ctr"))
                .andExpect(status().isOk());
    }

    @Test
    void uppercaseCtr_alsoServedOrFourOhFourGracefully() throws Exception {
        // Spring's URL-mapping is case-sensitive — uppercase /CTR should 404, not crash.
        mockMvc.perform(get("/reporting/regulatory/CTR"))
                .andExpect(status().isNotFound());
    }
}
