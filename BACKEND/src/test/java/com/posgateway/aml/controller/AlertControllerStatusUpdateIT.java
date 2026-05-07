package com.posgateway.aml.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.alert.AlertDispositionService;
import com.posgateway.aml.service.rules.RuleEffectivenessService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for {@link AlertController#updateAlertStatus}.
 *
 * <p>Verifies that PUT {@code /alerts/{id}/status} resolves and persists
 * the new status — the endpoint that backs the Alerts page bulk-action and
 * inline status changes.
 */
@WebMvcTest(controllers = AlertController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AlertControllerStatusUpdateIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertRepository alertRepository;

    @MockBean
    private AlertDispositionService alertDispositionService;

    @MockBean
    private PspIsolationService pspIsolationService;

    @MockBean
    private MerchantRepository merchantRepository;

    @MockBean
    private RuleEffectivenessService ruleEffectivenessService;

    @Test
    void updateStatus_withMatchingPspMerchant_persistsAndReturns200() throws Exception {
        Long pspId = 7L;

        Alert alert = new Alert();
        alert.setAlertId(123L);
        alert.setStatus("open");
        alert.setMerchantId(99L);

        Psp psp = Psp.builder().pspId(pspId).build();
        Merchant merchant = new Merchant();
        merchant.setMerchantId(99L);
        merchant.setPsp(psp);

        when(pspIsolationService.getCurrentUserPspId()).thenReturn(pspId);
        when(alertRepository.findById(123L)).thenReturn(Optional.of(alert));
        when(merchantRepository.findById(99L)).thenReturn(Optional.of(merchant));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/alerts/123/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "resolved"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"));
    }
}
