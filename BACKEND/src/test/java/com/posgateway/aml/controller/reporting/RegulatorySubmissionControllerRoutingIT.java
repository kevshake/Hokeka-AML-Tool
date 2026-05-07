package com.posgateway.aml.controller.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.dto.reporting.RegulatorySubmissionDTO;
import com.posgateway.aml.dto.reporting.RegulatorySubmissionRequest;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.service.reporting.RegulatorySubmissionService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for {@link RegulatorySubmissionController} routing fix.
 *
 * <p>Was {@code @RequestMapping("/api/reports")} which doubled with the
 * {@code /api/v1} context-path. Fixed to {@code "/reports"}.
 *
 * <p>Verifies POST {@code /reports/{id}/submit} resolves (not 404) post-fix.
 */
@WebMvcTest(controllers = RegulatorySubmissionController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RegulatorySubmissionControllerRoutingIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegulatorySubmissionService submissionService;

    @MockBean
    private PspIsolationService pspIsolationService;

    @org.junit.jupiter.api.AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitToRegulator_isReachable_notFourOhFour() throws Exception {
        // Set up authentication with a User principal (controller casts auth.getPrincipal() to User).
        User principal = new User();
        principal.setId(42L);
        principal.setUsername("compliance");
        Psp psp = new Psp();
        psp.setPspId(1L);
        principal.setPsp(psp);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", Collections.emptyList()));

        when(pspIsolationService.sanitizePspId(any())).thenReturn(1L);
        when(submissionService.submitToFinCEN(anyLong(), anyLong(), anyLong()))
                .thenReturn(new RegulatorySubmissionDTO());

        RegulatorySubmissionRequest req = new RegulatorySubmissionRequest();
        req.setRegulatorCode("FINCEN");
        req.setPspId(1L);

        mockMvc.perform(post("/reports/123/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void doubledUpLegacyPath_returns404() throws Exception {
        RegulatorySubmissionRequest req = new RegulatorySubmissionRequest();
        req.setRegulatorCode("FINCEN");

        mockMvc.perform(post("/api/reports/123/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }
}
