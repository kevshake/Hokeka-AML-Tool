package com.posgateway.aml.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Ultra High Throughput Configuration
 * Optimized for 30,000+ concurrent requests.
 *
 * <p><b>Threading model:</b> the transaction path is I/O-bound (DB, screening, Kafka) and runs
 * on <b>virtual threads</b>. The feature-extraction and scoring paths are <b>CPU-bound</b>
 * (ND4J/DL4J tensor math, model inference); virtual threads give them no benefit and would only
 * add scheduling overhead, so they stay on bounded <b>platform-thread</b> pools where real
 * parallelism is capped by cores. This is the "use virtual threads wherever they can serve"
 * principle: I/O yes, CPU no.
 */
@Configuration
@EnableAsync
public class UltraHighThroughputConfig {

    @Value("${ultra.throughput.core.pool.size:500}")
    private int corePoolSize;

    @Value("${ultra.throughput.max.pool.size:2000}")
    private int maxPoolSize;

    @Value("${ultra.throughput.queue.capacity:10000}")
    private int queueCapacity;

    /**
     * Ultra-high throughput executor for transaction processing (I/O-bound).
     * Virtual-thread-per-task; the concurrency limit reuses the configured max pool size as a
     * downstream backpressure guard (the role the old CallerRunsPolicy played), bounding how
     * many transactions hit the DB / screening APIs at once.
     */
    @Bean(name = "ultraTransactionExecutor")
    public Executor ultraTransactionExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("ultra-txn-vt-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(maxPoolSize);
        return executor;
    }

    /**
     * Feature extraction executor (CPU-bound: ND4J feature math) — intentionally kept on a
     * bounded platform-thread pool. Virtual threads do not speed up CPU-bound work.
     */
    @Bean(name = "ultraFeatureExtractionExecutor")
    public Executor ultraFeatureExtractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize / 2);
        executor.setMaxPoolSize(maxPoolSize / 2);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ultra-feature-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Scoring service executor (CPU-bound: model inference) — intentionally kept on a bounded
     * platform-thread pool for the same reason as feature extraction.
     */
    @Bean(name = "ultraScoringExecutor")
    public Executor ultraScoringExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ultra-scoring-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
