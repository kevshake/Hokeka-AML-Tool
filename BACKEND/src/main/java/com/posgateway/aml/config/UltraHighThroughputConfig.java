package com.posgateway.aml.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Ultra High Throughput Configuration
 * Optimized for 30,000+ concurrent requests
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
     * Ultra-high throughput executor for transaction processing
     * Designed for 30K+ concurrent requests
     */
    @Bean(name = "ultraTransactionExecutor")
    public Executor ultraTransactionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ultra-txn-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        
        // Rejected execution policy: Caller runs for backpressure
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        return executor;
    }

    /**
     * Feature extraction executor for parallel processing
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
     * Scoring service executor
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

