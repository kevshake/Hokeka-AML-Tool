package com.posgateway.aml.controller.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.compliance.ComplianceCalendarService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for {@link ComplianceCalendarController#createDeadlineFrontendShape}.
 *
 * <p>The frontend posts {@code { title, description?, dueDate, deadlineType }}.
 * Since {@link ComplianceDeadline} has no title column, the controller folds
 * {@code title} into {@code description}. This test verifies the persistence
 * call receives a description that contains the supplied title.
 */
@WebMvcTest(controllers = ComplianceCalendarController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ComplianceCalendarCreateIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ComplianceCalendarService complianceCalendarService;

    @MockBean
    private UserRepository userRepository;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createDeadline_foldsTitleIntoDescription() throws Exception {
        // Authenticated PSP user — provides currentPspId().
        User user = new User();
        user.setUsername("admin");
        Psp psp = Psp.builder().pspId(5L).build();
        user.setPsp(psp);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "x", Collections.emptyList()));

        ComplianceDeadline persisted = new ComplianceDeadline();
        persisted.setId(1L);
        persisted.setDeadlineType("SAR_FILING");
        when(complianceCalendarService.createDeadline(
                eq("SAR_FILING"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                eq(5L))).thenReturn(persisted);

        Map<String, Object> body = new HashMap<>();
        body.put("title", "Q1 SAR");
        body.put("description", "Quarterly review");
        body.put("dueDate", "2026-06-30T00:00:00");
        body.put("deadlineType", "SAR_FILING");

        mockMvc.perform(post("/compliance/calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
        verify(complianceCalendarService).createDeadline(
                eq("SAR_FILING"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                descCaptor.capture(),
                org.mockito.ArgumentMatchers.isNull(),
                eq(5L));

        // Title must be visible in the persisted description (entity has no title col).
        assertThat(descCaptor.getValue()).contains("Q1 SAR");
        assertThat(descCaptor.getValue()).contains("Quarterly review");
    }
}
