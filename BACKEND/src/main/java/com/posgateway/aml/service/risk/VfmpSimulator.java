package com.posgateway.aml.service.risk;

import com.posgateway.aml.model.MerchantMetrics;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Visa VFMP Simulator
 * Rules engine for Visa Fraud Monitoring Program
 */
// @RequiredArgsConstructor removed
@Service
public class VfmpSimulator {

    public enum VfmpStage {
        NORMAL,
        WATCH, // Internal early warning
        WARNING, // Approaching scheme thresholds
        HIGH_RISK,
        CRITICAL;

        public boolean isActionable() {
            return this != NORMAL;
        }
    }

    /**
     * Evaluate merchant against VFMP rules
     */
    public VfmpResult evaluate(MerchantMetrics metrics) {
        double fraudRate = metrics.getFraudRate();
        long fraudTx = metrics.getFraudTx();

        VfmpStage stage = VfmpStage.NORMAL;

        // Logic based on user requirements
        // Critical: Rate >= 1.5% and Vol >= 500
        if (fraudRate >= 0.015 && fraudTx >= 500) {
            stage = VfmpStage.CRITICAL;
        }
        // High Risk: Rate >= 0.9% and Vol >= 100
        else if (fraudRate >= 0.009 && fraudTx >= 100) {
            stage = VfmpStage.HIGH_RISK;
        }
        // Warning: Rate >= 0.6% and Vol >= 75
        else if (fraudRate >= 0.006 && fraudTx >= 75) {
            stage = VfmpStage.WARNING;
        }
        // Watch: Rate >= 0.4% and Vol >= 50
        else if (fraudRate >= 0.004 && fraudTx >= 50) {
            stage = VfmpStage.WATCH;
        }

        return new VfmpResult(stage, fraudRate, fraudTx);
    }

    public static class VfmpResult {
        private final VfmpStage stage;
        private final double fraudRate;
        private final long fraudVolume;

        public VfmpResult(VfmpStage stage, double fraudRate, long fraudVolume) {
            this.stage = stage;
            this.fraudRate = fraudRate;
            this.fraudVolume = fraudVolume;
        }

        public VfmpStage getStage() {
            return stage;
        }

        public double getFraudRate() {
            return fraudRate;
        }

        public long getFraudVolume() {
            return fraudVolume;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("stage", stage.name());
            map.put("rate", fraudRate);
            map.put("volume", fraudVolume);
            return map;
        }
    }
}
