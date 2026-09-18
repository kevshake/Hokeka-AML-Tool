package com.posgateway.aml.service.risk;

import com.posgateway.aml.model.MerchantMetrics;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Mastercard HECM Simulator
 * Rules engine for High Excessive Chargeback Merchant program
 */
// @RequiredArgsConstructor removed
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

    /**
     * Evaluate merchant against HECM rules
     */
    public HecmResult evaluate(MerchantMetrics metrics) {
        double cbRatio = metrics.getChargebackRatio();
        long cbCount = metrics.getChargebackCount();

        HecmStage stage = HecmStage.NORMAL;

        // Logic based on user requirements
        // Excessive: Ratio >= 1.5% and Count >= 300
        if (cbRatio >= 0.015 && cbCount >= 300) {
            stage = HecmStage.EXCESSIVE;
        }
        // High: Ratio >= 1.0% and Count >= 100
        else if (cbRatio >= 0.010 && cbCount >= 100) {
            stage = HecmStage.HIGH;
        }
        // Watch: Ratio >= 0.65% and Count >= 75
        else if (cbRatio >= 0.0065 && cbCount >= 75) {
            stage = HecmStage.WATCH;
        }

        return new HecmResult(stage, cbRatio, cbCount);
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
