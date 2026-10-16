package com.posgateway.aml.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression (sim run 2026-10-16): POST /reports/generate stayed PENDING forever because the
 * background worker had no security context ("User not authenticated" in PspIsolationService).
 */
class AsyncConfigSecurityContextTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void backgroundExecutorRunsTasksAsTheSubmittingUser() throws Exception {
        AsyncConfig config = new AsyncConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(config, "backgroundConcurrency", 4);
        Executor executor = config.backgroundTaskExecutor();
        Authentication caller = new UsernamePasswordAuthenticationToken("card.admin", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(caller);

        CompletableFuture<Authentication> seen = new CompletableFuture<>();
        executor.execute(() -> seen.complete(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(seen.get(5, TimeUnit.SECONDS)).isSameAs(caller);
    }

    @Test
    void decoratedTaskRestoresTheWorkersPreviousContext() {
        Authentication caller = new UsernamePasswordAuthenticationToken("caller", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(caller);
        Runnable decorated = AsyncConfig.withCallerSecurityContext(() -> { });

        SecurityContextHolder.clearContext();
        decorated.run();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
