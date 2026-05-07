package com.posgateway.aml.controller.limits;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.limits.GlobalLimit;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.limits.LimitsManagementService;
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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for {@link LimitsManagementController#saveAmlLimits}.
 *
 * <p>POST {@code /limits/aml} with {@code transactionLimit} + {@code dailyLimit}
 * must persist as TWO {@link GlobalLimit} rows — one TRANSACTION and one DAILY.
 */
@WebMvcTest(controllers = LimitsManagementController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class LimitsAmlSaveIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LimitsManagementService limitsService;

    @MockBean
    private UserRepository userRepository;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void saveAmlLimits_persistsBothTransactionAndDailyRows() throws Exception {
        // Authenticated user — controller looks up via SecurityContextHolder.
        User principal = new User();
        principal.setId(7L);
        principal.setUsername("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(principal));

        // @AuthenticationPrincipal expects a Spring Security User, but the controller
        // also fetches the platform User via SecurityContextHolder for the userId.
        org.springframework.security.core.userdetails.User secUser =
                new org.springframework.security.core.userdetails.User("admin", "x", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(secUser, "x", Collections.emptyList()));

        when(limitsService.createGlobalLimit(any(GlobalLimit.class), any(Long.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "transactionLimit", 50000,
                "dailyLimit", 250000
        );

        mockMvc.perform(post("/limits/aml")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ArgumentCaptor<GlobalLimit> captor = ArgumentCaptor.forClass(GlobalLimit.class);
        verify(limitsService, times(2)).createGlobalLimit(captor.capture(), eq(7L));

        // Verify one row of each limit type was persisted.
        long txnCount = captor.getAllValues().stream()
                .filter(l -> "TRANSACTION".equals(l.getLimitType()))
                .count();
        long dailyCount = captor.getAllValues().stream()
                .filter(l -> "DAILY".equals(l.getLimitType()))
                .count();

        org.assertj.core.api.Assertions.assertThat(txnCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(dailyCount).isEqualTo(1);
    }
}
