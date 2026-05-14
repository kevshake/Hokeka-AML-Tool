package com.posgateway.aml.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cache Configuration
 *
 * <p>Uses Caffeine as the in-process L2 cache. Each named cache has its own
 * TTL and maximum size tuned for its access pattern:
 *
 * <ul>
 *   <li><b>psps</b> — PSP configuration records read on every transaction.
 *       15-min TTL, 500 entries max. Evicted on any PSP mutation.</li>
 *   <li><b>users</b> — UserDetails loaded by Spring Security on every
 *       authenticated request. 5-min TTL, 2 000 entries max. Evicted on
 *       password change, update, delete, or status toggle.</li>
 *   <li><b>sanctions</b> — Sanctions screening results proxied from the AML
 *       microservice. 10-min TTL, 50 000 entries max (one per screened name).
 *       Sanctions lists refresh daily, so a 10-min window is safe.</li>
 *   <li><b>dashboard-kpis</b> — Aggregated KPI figures for the analytics
 *       dashboard. 60-sec TTL, 10 entries max.</li>
 *   <li><b>cbk-config</b> — CBK per-PSP compliance configuration. 30-min
 *       TTL, 200 entries max.</li>
 *   <li><b>modelConfig</b>, <b>aggregateFeatures</b>, <b>riskScores</b>,
 *       <b>screeningResults</b> — legacy cache names preserved for existing
 *       callers. 15-min TTL, 1 000 entries max each.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                build("psps",              15, TimeUnit.MINUTES,  500),
                build("users",              5, TimeUnit.MINUTES, 2000),
                build("sanctions",         10, TimeUnit.MINUTES, 50_000),
                build("dashboard-kpis",    60, TimeUnit.SECONDS,    10),
                build("cbk-config",        30, TimeUnit.MINUTES,   200),
                // Legacy names — keep for binary compatibility
                build("modelConfig",       15, TimeUnit.MINUTES, 1000),
                build("aggregateFeatures", 15, TimeUnit.MINUTES, 1000),
                build("riskScores",        15, TimeUnit.MINUTES, 1000),
                build("screeningResults",  10, TimeUnit.MINUTES, 5000)
        ));
        return manager;
    }

    private static CaffeineCache build(String name, long duration, TimeUnit unit, long maxSize) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .expireAfterWrite(duration, unit)
                        .maximumSize(maxSize)
                        .recordStats()
                        .build());
    }
}
