package com.posgateway.aml.service.risk;

import com.posgateway.aml.model.MerchantMetrics;
import com.posgateway.aml.repository.AerospikeMetricsRepository;
import com.posgateway.aml.service.risk.HecmSimulator.HecmResult;
import com.posgateway.aml.service.risk.VfmpSimulator.VfmpResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

// @RequiredArgsConstructor removed
@Service
public class SchemeSimulatorService {

    private final AerospikeMetricsRepository metricsRepository;
    private final VfmpSimulator vfmpSimulator;
    private final HecmSimulator hecmSimulator;

    public SchemeSimulatorService(AerospikeMetricsRepository metricsRepository, VfmpSimulator vfmpSimulator,
            HecmSimulator hecmSimulator) {
        this.metricsRepository = metricsRepository;
        this.vfmpSimulator = vfmpSimulator;
        this.hecmSimulator = hecmSimulator;
    }

    /**
     * Assess merchant risk using VFMP and HECM simulators
     */
    public MerchantRiskAssessment assessMerchant(String merchantId) {
        // 1. Load rolling 30-day metrics
        MerchantMetrics metrics = metricsRepository.load30DayMetrics(merchantId);

        // 2. Run Simulators
        VfmpResult vfmpResult = vfmpSimulator.evaluate(metrics);
        HecmResult hecmResult = hecmSimulator.evaluate(metrics);

        // 3. Construct Assessment Result
        // 3. Construct Assessment Result
        return new MerchantRiskAssessment(merchantId, vfmpResult, hecmResult, metrics);
    }

    public static class MerchantRiskAssessment {
        private String merchantId;
        private VfmpResult vfmpResult;
        private HecmResult hecmResult;
        private MerchantMetrics metrics;

        public MerchantRiskAssessment(String merchantId, VfmpResult vfmpResult, HecmResult hecmResult,
                MerchantMetrics metrics) {
            this.merchantId = merchantId;
            this.vfmpResult = vfmpResult;
            this.hecmResult = hecmResult;
            this.metrics = metrics;
        }

        public String getMerchantId() {
            return merchantId;
        }

        public VfmpResult getVfmpResult() {
            return vfmpResult;
        }

        public HecmResult getHecmResult() {
            return hecmResult;
        }

        public MerchantMetrics getMetrics() {
            return metrics;
        }

        public static MerchantRiskAssessmentBuilder builder() {
            return new MerchantRiskAssessmentBuilder();
        }

        public static class MerchantRiskAssessmentBuilder {
            private String merchantId;
            private VfmpResult vfmpResult;
            private HecmResult hecmResult;
            private MerchantMetrics metrics;

            MerchantRiskAssessmentBuilder() {
            }

            public MerchantRiskAssessmentBuilder merchantId(String merchantId) {
                this.merchantId = merchantId;
                return this;
            }

            public MerchantRiskAssessmentBuilder vfmpResult(VfmpResult vfmpResult) {
                this.vfmpResult = vfmpResult;
                return this;
            }

            public MerchantRiskAssessmentBuilder hecmResult(HecmResult hecmResult) {
                this.hecmResult = hecmResult;
                return this;
            }

            public MerchantRiskAssessmentBuilder metrics(MerchantMetrics metrics) {
                this.metrics = metrics;
                return this;
            }

            public MerchantRiskAssessment build() {
                return new MerchantRiskAssessment(merchantId, vfmpResult, hecmResult, metrics);
            }
        }

        public boolean isHighRisk() {
            return vfmpResult.getStage().isActionable() || hecmResult.getStage().isActionable();
        }

        public Map<String, Object> toRiskDetails() {
            Map<String, Object> details = new HashMap<>();
            details.put("vfmp", vfmpResult.toMap());
            details.put("hecm", hecmResult.toMap());
            return details;
        }
    }
}
