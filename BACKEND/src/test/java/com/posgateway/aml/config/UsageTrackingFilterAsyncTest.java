package com.posgateway.aml.config;

import com.posgateway.aml.dto.psp.ApiUsageEvent;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.service.psp.ApiUsageTrackingService;
import jakarta.servlet.AsyncContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Regression (sim run 2026-10-16): every {@code POST /transactions/ingest} answered 200 with an empty
 * body because this filter wrapped the response in a ContentCachingResponseWrapper and flushed it
 * (Content-Length 0) when the async controller's initial dispatch returned.
 */
class UsageTrackingFilterAsyncTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAsPspUser(long pspId) {
        Psp psp = new Psp();
        psp.setPspId(pspId);
        User user = new User();
        user.setPsp(psp);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void asyncResponseBodyWrittenAfterInitialDispatchReachesTheClientAndIsBilledOnCompletion() throws Exception {
        ApiUsageTrackingService tracking = mock(ApiUsageTrackingService.class);
        UsageTrackingFilter filter = new UsageTrackingFilter(tracking);
        authenticateAsPspUser(5L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions/ingest");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AsyncContext[] started = new AsyncContext[1];

        // The controller starts async processing and returns without writing (CompletableFuture).
        filter.doFilter(request, response, (req, res) -> started[0] = req.startAsync());

        assertThat(response.isCommitted()).as("initial dispatch must not commit the response").isFalse();
        verify(tracking, never()).logRequest(org.mockito.ArgumentMatchers.any());

        // Async completion writes the decision, then completes.
        response.setStatus(200);
        response.getWriter().write("{\"txnId\":42,\"action\":\"ALLOW\"}");
        started[0].complete();

        assertThat(response.getContentAsString()).isEqualTo("{\"txnId\":42,\"action\":\"ALLOW\"}");
        ArgumentCaptor<ApiUsageEvent> event = ArgumentCaptor.forClass(ApiUsageEvent.class);
        verify(tracking).logRequest(event.capture());
        assertThat(event.getValue().getServiceType()).isEqualTo("TRANSACTION_MONITORING");
        assertThat(event.getValue().getResponseStatus()).isEqualTo(200);
        assertThat(event.getValue().getPspId()).isEqualTo(5L);
    }

    @Test
    void synchronousResponsePassesThroughUnchangedAndIsBilled() throws Exception {
        ApiUsageTrackingService tracking = mock(ApiUsageTrackingService.class);
        UsageTrackingFilter filter = new UsageTrackingFilter(tracking);
        authenticateAsPspUser(7L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/sanctions/screen");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
            res.getWriter().write("ok");
        });

        assertThat(response.getContentAsString()).isEqualTo("ok");
        ArgumentCaptor<ApiUsageEvent> event = ArgumentCaptor.forClass(ApiUsageEvent.class);
        verify(tracking).logRequest(event.capture());
        assertThat(event.getValue().getResponseStatus()).isEqualTo(201);
        assertThat(event.getValue().getPspId()).isEqualTo(7L);
    }
}
