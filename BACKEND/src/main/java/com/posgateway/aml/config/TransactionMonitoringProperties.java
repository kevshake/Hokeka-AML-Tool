package com.posgateway.aml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Transaction Monitoring Configuration Properties
 * All transaction monitoring settings loaded from application.properties
 * No hardcoding - all values configurable via properties file or environment variables
 */
@Component
@ConfigurationProperties(prefix = "transaction.monitoring")
public class TransactionMonitoringProperties {

    private boolean enabled = true;
    private int batchSize = 100;
    private int intervalSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }
}

