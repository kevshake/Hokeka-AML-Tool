package com.posgateway.aml.service.risk;

import com.posgateway.aml.entity.risk.SchemeProgramThreshold;
import com.posgateway.aml.model.MerchantMetrics;
import com.posgateway.aml.repository.risk.SchemeProgramThresholdRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Visa VFMP Simulator
 * Rules engine for Visa Fraud Monitoring Program
 */
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

    private final SchemeProgramThresholdRepository thresholdRepository;

    /** Creates a simulator backed by effective-dated VFMP thresholds. */
    public VfmpSimulator(SchemeProgramThresholdRepository thresholdRepository) {
        this.thresholdRepository = thresholdRepository;
    }

    /**
     * Evaluate merchant against VFMP rules
     */
    public VfmpResult evaluate(MerchantMetrics metrics) {
        double fraudRate = metrics.getFraudRate();
        long fraudTx = metrics.getFraudTx();
        var rows = thresholdRepository.findEffectiveRows("VFMP", OffsetDateTime.now());
        SchemeProgramThreshold row = rows == null ? null : rows.stream().findFirst().orElse(null);

        double criticalRate = value(row != null ? row.getCriticalRate() : null, 0.015);
        long criticalVol = value(row != null ? row.getCriticalVolume() : null, 500L);
        double highRate = value(row != null ? row.getHighRate() : null, 0.009);
        long highVol = value(row != null ? row.getHighVolume() : null, 100L);
        double warningRate = value(row != null ? row.getWarningRate() : null, 0.006);
        long warningVol = value(row != null ? row.getWarningVolume() : null, 75L);
        double watchRate = value(row != null ? row.getWatchRate() : null, 0.004);
        long watchVol = value(row != null ? row.getWatchVolume() : null, 50L);

        VfmpStage stage = VfmpStage.NORMAL;
        if (fraudRate >= criticalRate && fraudTx >= criticalVol) {
            stage = VfmpStage.CRITICAL;
        } else if (fraudRate >= highRate && fraudTx >= highVol) {
            stage = VfmpStage.HIGH_RISK;
        } else if (fraudRate >= warningRate && fraudTx >= warningVol) {
            stage = VfmpStage.WARNING;
        } else if (fraudRate >= watchRate && fraudTx >= watchVol) {
            stage = VfmpStage.WATCH;
        }

        return new VfmpResult(stage, fraudRate, fraudTx);
    }

    /** Uses a configured decimal threshold when present, otherwise its regulatory fallback. */
    private static double value(Double configured, double fallback) {
        return configured != null ? configured : fallback;
    }

    /** Uses a configured volume threshold when present, otherwise its regulatory fallback. */
    private static long value(Long configured, long fallback) {
        return configured != null ? configured : fallback;
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
