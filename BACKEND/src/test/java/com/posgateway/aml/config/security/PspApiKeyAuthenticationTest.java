package com.posgateway.aml.config.security;

import com.posgateway.aml.config.CustomAuthenticationFailureHandler;
import com.posgateway.aml.config.SecurityConfig;
import com.posgateway.aml.controller.TransactionController;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.service.auth.CustomUserDetailsService;
import com.posgateway.aml.service.auth.OnboardingInviteService;
import com.posgateway.aml.service.security.PspApiKeyService;
import com.posgateway.aml.service.security.PspApiKeyService.AuthenticatedApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real {@link SecurityConfig} filter chain with {@link PspApiKeyAuthenticationFilter} to
 * prove that a PSP API key authenticates machine ingest (both header forms, through the async
 * dispatch the real ingest endpoint uses), binds the PSP, and is ignored everywhere else, while
 * Console session auth keeps working.
 */
@WebMvcTest(controllers = PspApiKeyAuthenticationTest.ProbeController.class, properties = {
        "spring.data.jpa.repositories.enabled=false",
        "hokeka.api-keys.rate-limit-per-minute=3"
})
@ContextConfiguration(classes = {
        SecurityConfig.class,
        PspApiKeyAuthenticationFilter.class,
        OnboardingInviteAuthenticationFilter.class,
        PspApiKeyAuthenticationTest.ProbeController.class,
        PspApiKeyAuthenticationTest.Encoders.class
})
class PspApiKeyAuthenticationTest {

    static final String GOOD = "hka_goodgoodgoodgoodgoodgoodgoodgoodgoodgoo";
    static final String OTHER = "hka_othrothrothrothrothrothrothrothrothrot";

    /** The application's PasswordEncoder lives in AppConfig, which this slice does not load. */
    @org.springframework.context.annotation.Configuration
    static class Encoders {
        @org.springframework.context.annotation.Bean
        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
            return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        }
    }

    /** Stand-in for the ingest controller with the same path and role gate as the real one. */
    @RestController
    static class ProbeController {
        @PostMapping("/transactions/ingest")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PSP_ADMIN','PSP_USER','PSP_INTEGRATION')")
        CompletableFuture<Map<String, Object>> ingest(@AuthenticationPrincipal Object principal) {
            User u = (User) principal;
            return CompletableFuture.completedFuture(Map.of(
                    "user", u.getUsername(),
                    "psp", u.getPsp() == null ? -1L : u.getPsp().getPspId()));
        }

        @GetMapping("/alerts")
        Map<String, Object> alerts() {
            return Map.of("ok", true);
        }
    }

    @Autowired MockMvc mvc;
    @MockBean PspApiKeyService apiKeyService;
    @MockBean CustomUserDetailsService userDetailsService;
    @MockBean CustomAuthenticationFailureHandler failureHandler;
    @MockBean OnboardingInviteService onboardingInviteService;

    @BeforeEach
    void keys() {
        Psp psp = new Psp();
        psp.setPspId(41L);
        psp.setStatus("ACTIVE");
        when(apiKeyService.authenticate(anyString())).thenReturn(Optional.empty());
        when(apiKeyService.authenticate(GOOD)).thenReturn(Optional.of(new AuthenticatedApiKey(5L, GOOD.substring(0, 12), psp)));
        when(apiKeyService.authenticate(OTHER)).thenReturn(Optional.of(new AuthenticatedApiKey(6L, OTHER.substring(0, 12), psp)));
    }

    private void expectIngestOk(String header, String value) throws Exception {
        MvcResult started = mvc.perform(post("/transactions/ingest").header(header, value))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.psp").value(41))
                .andExpect(jsonPath("$.user").value("api-key:" + GOOD.substring(0, 12)));
    }

    @Test
    void xApiKeyHeaderAuthenticatesIngestAndBindsPsp() throws Exception {
        expectIngestOk("X-Api-Key", GOOD);
    }

    @Test
    void bearerApiKeyAuthenticatesIngest() throws Exception {
        expectIngestOk("Authorization", "Bearer " + GOOD);
    }

    @Test
    void invalidKeyGets401JsonAndNoKeyIsNotAuthenticated() throws Exception {
        mvc.perform(post("/transactions/ingest").header("X-Api-Key", "hka_revoked"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        int anonymous = mvc.perform(post("/transactions/ingest")).andReturn().getResponse().getStatus();
        assertThat(anonymous).isIn(401, 403);
    }

    @Test
    void keyIsIgnoredOutsideMachineEndpoints() throws Exception {
        int status = mvc.perform(get("/alerts").header("X-Api-Key", GOOD)).andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @Test
    void perKeyRateLimitAnswers429() throws Exception {
        for (int i = 0; i < 3; i++) {
            MvcResult r = mvc.perform(post("/transactions/ingest").header("X-Api-Key", OTHER)).andReturn();
            mvc.perform(asyncDispatch(r)).andExpect(status().isOk());
        }
        mvc.perform(post("/transactions/ingest").header("X-Api-Key", OTHER))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @WithMockUser(username = "console.user", roles = "PSP_USER")
    void consoleSessionStillWorksWithoutKey() throws Exception {
        mvc.perform(get("/alerts")).andExpect(status().isOk());
    }

    @Test
    void realIngestEndpointsAdmitTheMachineRole() throws Exception {
        for (String m : new String[]{"ingestTransaction", "batchIngestTransactions", "getTransactionAssessment"}) {
            java.lang.reflect.Method method = java.util.Arrays.stream(TransactionController.class.getMethods())
                    .filter(x -> x.getName().equals(m)).findFirst().orElseThrow();
            assertThat(method.getAnnotation(PreAuthorize.class).value()).contains("PSP_INTEGRATION");
        }
    }
}
