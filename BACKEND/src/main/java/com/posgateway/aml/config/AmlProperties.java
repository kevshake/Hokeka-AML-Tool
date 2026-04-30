package com.posgateway.aml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AML Configuration Properties
 * All AML-related settings loaded from application.properties
 * No hardcoding - all values configurable via properties file or environment variables
 */
@Component
@ConfigurationProperties(prefix = "aml")
public class AmlProperties {

    private boolean enabled = true;
    private RiskThreshold risk = new RiskThreshold();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RiskThreshold getRisk() {
        return risk;
    }

    public void setRisk(RiskThreshold risk) {
        this.risk = risk;
    }

    public static class RiskThreshold {
        private int low = 30;
        private int medium = 60;
        private int high = 80;

        public int getLow() {
            return low;
        }

        public void setLow(int low) {
            this.low = low;
        }

        public int getMedium() {
            return medium;
        }

        public void setMedium(int medium) {
            this.medium = medium;
        }

        public int getHigh() {
            return high;
        }

        public void setHigh(int high) {
            this.high = high;
        }
    }
}

