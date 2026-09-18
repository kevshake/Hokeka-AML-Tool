package com.posgateway.aml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Fraud Detection Configuration Properties
 * All fraud detection settings loaded from application.properties
 * No hardcoding - all values configurable via properties file or environment variables
 */
@Component
@ConfigurationProperties(prefix = "fraud")
public class FraudProperties {

    private boolean enabled = true;
    private Scoring scoring = new Scoring();
    private Velocity velocity = new Velocity();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Scoring getScoring() {
        return scoring;
    }

    public void setScoring(Scoring scoring) {
        this.scoring = scoring;
    }

    public Velocity getVelocity() {
        return velocity;
    }

    public void setVelocity(Velocity velocity) {
        this.velocity = velocity;
    }

    public static class Scoring {
        private boolean enabled = true;
        private int threshold = 70;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }
    }

    public static class Velocity {
        private boolean checkEnabled = true;
        private int windowMinutes = 60;
        private int maxTransactions = 10;

        public boolean isCheckEnabled() {
            return checkEnabled;
        }

        public void setCheckEnabled(boolean checkEnabled) {
            this.checkEnabled = checkEnabled;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(int windowMinutes) {
            this.windowMinutes = windowMinutes;
        }

        public int getMaxTransactions() {
            return maxTransactions;
        }

        public void setMaxTransactions(int maxTransactions) {
            this.maxTransactions = maxTransactions;
        }
    }
}

