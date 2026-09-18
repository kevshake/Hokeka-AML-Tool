package com.posgateway.aml.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proof that the {@link AsyncConfig} executors actually dispatch work onto virtual threads
 * (Project Loom). Requires a Java 21+ runtime (the build/CI JVM here is 21+); {@code isVirtual()}
 * is the ground-truth check that Spring's {@code setVirtualThreads(true)} took effect.
 *
 * <p>The {@code applicationTaskExecutor}/{@code taskExecutor} case is the important one: it is the
 * default executor for every unqualified {@code @Async} method. Before it was added, those methods
 * silently ran on platform threads because Spring Boot's auto-configured virtual default backs off
 * ({@code @ConditionalOnMissingBean(Executor.class)}) once the app declares its own executors.
 */
class VirtualThreadExecutorTest {

    private Executor build(String beanFactoryMethod) throws Exception {
        AsyncConfig cfg = new AsyncConfig();
        ReflectionTestUtils.setField(cfg, "amlConcurrency", 2000);
        ReflectionTestUtils.setField(cfg, "backgroundConcurrency", 200);
        ReflectionTestUtils.setField(cfg, "transactionConcurrency", 1000);
        ReflectionTestUtils.setField(cfg, "meteringConcurrency", 500);
        ReflectionTestUtils.setField(cfg, "defaultConcurrency", 1000);
        ReflectionTestUtils.setField(cfg, "defaultTerminationTimeoutMs", 20000L);
        return (Executor) AsyncConfig.class.getMethod(beanFactoryMethod).invoke(cfg);
    }

    private boolean runsOnVirtualThread(Executor executor) throws Exception {
        CompletableFuture<Boolean> onVirtual = new CompletableFuture<>();
        executor.execute(() -> onVirtual.complete(Thread.currentThread().isVirtual()));
        return onVirtual.get(5, TimeUnit.SECONDS);
    }

    @Test
    void defaultAsyncExecutorRunsOnVirtualThreads() throws Exception {
        assertTrue(runsOnVirtualThread(build("applicationTaskExecutor")),
                "the default (unqualified @Async) executor must run tasks on virtual threads");
    }

    @Test
    void qualifiedDomainExecutorsRunOnVirtualThreads() throws Exception {
        assertTrue(runsOnVirtualThread(build("taskExecutor")),
                "amlTaskExecutor must run tasks on virtual threads");
        assertTrue(runsOnVirtualThread(build("backgroundTaskExecutor")),
                "backgroundTaskExecutor must run tasks on virtual threads");
        assertTrue(runsOnVirtualThread(build("transactionExecutor")),
                "transactionExecutor must run tasks on virtual threads");
        assertTrue(runsOnVirtualThread(build("meteringExecutor")),
                "meteringExecutor must run tasks on virtual threads");
    }
}
