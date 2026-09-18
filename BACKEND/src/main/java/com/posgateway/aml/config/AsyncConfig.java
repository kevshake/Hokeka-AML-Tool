package com.posgateway.aml.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

/**
 * Async executor configuration.
 *
 * <p>All executors here back {@code @Async} paths that are dominated by <b>blocking I/O</b>
 * (DB reads/writes, sanctions/screening HTTP calls, Kafka publishing, metering events), so each
 * task now runs on a <b>virtual thread</b> (Java 21+, via {@code spring.threads.virtual.enabled}
 * and these {@link SimpleAsyncTaskExecutor}s with {@code setVirtualThreads(true)}). Virtual
 * threads are cheap to create and park while blocked, so we no longer size a bounded platform
 * pool with a big queue.
 *
 * <p>The only guard we keep is a <b>concurrency limit</b> — a semaphore that bounds how many
 * tasks run at once so a burst cannot exhaust the HikariCP connection pool or a rate-limited
 * external API. When the limit is reached the (also-virtual) submitting thread parks briefly,
 * giving natural backpressure. Limits are tunable via properties; they are ceilings on
 * downstream pressure, not thread-pool sizes.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${async.aml.concurrency:2000}")
    private int amlConcurrency;

    @Value("${async.background.concurrency:200}")
    private int backgroundConcurrency;

    @Value("${async.transaction.concurrency:1000}")
    private int transactionConcurrency;

    @Value("${async.metering.concurrency:500}")
    private int meteringConcurrency;

    @Value("${async.default.concurrency:1000}")
    private int defaultConcurrency;

    /** Grace period (ms) to let in-flight fire-and-forget tasks finish on shutdown. */
    @Value("${async.default.termination-timeout-ms:20000}")
    private long defaultTerminationTimeoutMs;

    /** Build a virtual-thread-per-task executor with a downstream concurrency guard. */
    private Executor virtualExecutor(String threadNamePrefix, int concurrencyLimit) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(threadNamePrefix);
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(concurrencyLimit);
        return executor;
    }

    /**
     * Default executor for <b>unqualified</b> {@code @Async} methods (audit logging, notifications,
     * email, workflow automation, API-usage tracking, case enrichment, …).
     *
     * <p>This bean is required for those paths to actually run on virtual threads. Spring Boot's
     * auto-configured virtual default executor (named {@code applicationTaskExecutor}/
     * {@code taskExecutor}) is annotated {@code @ConditionalOnMissingBean(Executor.class)} — and
     * because this app defines several custom {@code Executor} beans it <b>backs off</b>. Without
     * a replacement, {@code @EnableAsync} falls back to a plain platform-thread
     * {@link SimpleAsyncTaskExecutor}, silently defeating virtual threads for every bare
     * {@code @Async}. Registering this bean under the well-known names restores the virtual default.
     *
     * <p>Unlike the domain executors it also sets a task-termination timeout so a deploy/restart
     * waits briefly for in-flight audit/notification tasks instead of dropping them.
     */
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    public Executor applicationTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("App-VT-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(defaultConcurrency);
        executor.setTaskTerminationTimeout(defaultTerminationTimeoutMs);
        return executor;
    }

    @Bean(name = "amlTaskExecutor")
    public Executor taskExecutor() {
        return virtualExecutor("AML-VT-", amlConcurrency);
    }

    @Bean(name = "backgroundTaskExecutor")
    public Executor backgroundTaskExecutor() {
        return virtualExecutor("BG-VT-", backgroundConcurrency);
    }

    /**
     * Dedicated executor for asynchronous transaction fraud processing.
     * {@code AsyncFraudDetectionOrchestrator#processTransactionAsync} is annotated
     * {@code @Async("transactionExecutor")}.
     */
    @Bean(name = "transactionExecutor")
    public Executor transactionExecutor() {
        return virtualExecutor("Txn-VT-", transactionConcurrency);
    }

    /**
     * Dedicated executor for billing/metering event publishing, so metering never competes with
     * transaction processing for threads.
     *
     * <p>Metering is recorded by {@code ApiUsageTrackingService.logRequest}, driven by
     * {@code UsageTrackingFilter} for every mapped HTTP endpoint. Any future service-layer metering
     * should call that service directly and qualify with this executor — do NOT re-introduce a
     * parallel publisher for endpoints the filter already meters, or the tenant is billed twice.
     */
    @Bean(name = "meteringExecutor")
    public Executor meteringExecutor() {
        return virtualExecutor("Metering-VT-", meteringConcurrency);
    }
}
