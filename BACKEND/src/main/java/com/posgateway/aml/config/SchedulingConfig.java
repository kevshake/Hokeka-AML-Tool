package com.posgateway.aml.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enable Spring Scheduling for periodic tasks
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Enables @Scheduled annotations
}
