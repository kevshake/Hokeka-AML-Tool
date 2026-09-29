package com.posgateway.aml.service.risk;

import com.posgateway.aml.entity.risk.SchemeProgramThreshold;
import com.posgateway.aml.model.MerchantMetrics;
import com.posgateway.aml.repository.risk.SchemeProgramThresholdRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Mastercard HECM Simulator
 * Rules engine for High Excessive Chargeback Merchant program
 */
@Service
public class HecmSimulator {

    public enum HecmStage {
        NORMAL,
        WATCH, // Internal early warning
        HIGH,
        EXCESSIVE;

        public boolean isActionable() {
            return this != NORMAL;
        }
    }

    private final SchemeProgramThresholdRepository thresholdRepository;

    public HecmSimulator(SchemeProgramThresholdRepository thresholdRepository) {
        this.thresholdRepository = thresholdRepository;
    }

    /**
     * Evaluate merchant against HECM rules
     */
    public HecmResult evaluate(MerchantMetrics metrics) {
        double cbRatio = metrics.getChargebackRatio();
        long cbCount = metrics.getChargebackCount();
        var rows = thresholdRepository.findEffectiveRows("HECM", OffsetDateTime.now());
        SchemeProgramThreshold row = rows == null ? null : rows.stream().findFirst().orElse(null);

        double excessiveRate = value(row != null ? row.getCriticalRate() : null, 0.015);
        long excessiveCount = value(row != null ? row.getCriticalVolume() : null, 300L);
        double highRate = value(row != null ? row.getHighRate() : null, 0.010);
        long highCount = value(row != null ? row.getHighVolume() : null, 100L);
        double watchRate = value(row != null ? row.getWatchRate() : null, 0.0065);
        long watchCount = value(row != null ? row.getWatchVolume() : null, 75L);

        HecmStage stage = HecmStage.NORMAL;
        if (cbRatio >= excessiveRate && cbCount >= excessiveCount) {
            stage = HecmStage.EXCESSIVE;
        } else if (cbRatio >= highRate && cbCount >= highCount) {
            stage = HecmStage.HIGH;
        } else if (cbRatio >= watchRate && cbCount >= watchCount) {
            stage = HecmStage.WATCH;
        }

        return new HecmResult(stage, cbRatio, cbCount);
    }

    private static double value(Double configured, double fallback) {
        return configured != null ? configured : fallback;
    }

    private static long value(Long configured, long fallback) {
        return configured != null ? configured : fallback;
    }

    public static class HecmResult {
        private final HecmStage stage;
        private final double ratio;
        private final long count;

        public HecmResult(HecmStage stage, double ratio, long count) {
            this.stage = stage;
            this.ratio = ratio;
            this.count = count;
        }

        public HecmStage getStage() {
            return stage;
        }

        public double getRatio() {
            return ratio;
        }

        public long getCount() {
            return count;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("stage", stage.name());
            map.put("ratio", ratio);
            map.put("count", count);
            return map;
        }
    }
}
