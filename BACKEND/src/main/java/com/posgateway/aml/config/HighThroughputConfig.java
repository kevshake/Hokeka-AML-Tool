package com.posgateway.aml.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * High Throughput Configuration
 * Note: Cache configuration moved to CacheConfig to avoid bean definition
 * conflicts
 */
@Configuration
@EnableCaching
public class HighThroughputConfig {
    // Cache configuration is now centralized in CacheConfig.java
}
