package com.posgateway.aml.service.deeplearning;

import jakarta.annotation.PostConstruct;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Runs anomaly detection only with an explicitly configured, trained DL4J autoencoder. */
@Service
@org.springframework.context.annotation.Profile("!h2")
public class DL4JAnomalyService {

    private static final Logger logger = LoggerFactory.getLogger(DL4JAnomalyService.class);
    private static final int INPUT_SIZE = 20;

    private final RedisTemplate<String, Object> redisTemplate;
    private MultiLayerNetwork autoencoder;

    @Value("${dl4j.anomaly.threshold:0.5}")
    private double anomalyThreshold;

    @Value("${dl4j.enabled:false}")
    private boolean dl4jEnabled;

    @Value("${dl4j.model.path:}")
    private String modelPath;

    public DL4JAnomalyService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void initializeModel() {
        if (!dl4jEnabled) {
            logger.info("DL4J anomaly detection is disabled");
            return;
        }
        if (modelPath == null || modelPath.isBlank()) {
            throw new IllegalStateException("dl4j.model.path is required when dl4j.enabled=true");
        }
        try {
            File modelFile = new File(modelPath);
            if (!modelFile.isFile()) {
                throw new IllegalStateException("DL4J model file does not exist: " + modelFile.getAbsolutePath());
            }
            autoencoder = ModelSerializer.restoreMultiLayerNetwork(modelFile, false);
            long inputSize = autoencoder.layerInputSize(0);
            long outputSize = autoencoder.layerSize(autoencoder.getnLayers() - 1);
            if (inputSize != INPUT_SIZE || outputSize != INPUT_SIZE) {
                throw new IllegalStateException(
                        "DL4J model must have " + INPUT_SIZE + " inputs and outputs, got "
                                + inputSize + " and " + outputSize);
            }
            logger.info("Loaded trained DL4J anomaly model from {}", modelFile.getAbsolutePath());
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to load trained DL4J anomaly model", failure);
        }
    }

    public boolean isEnabled() {
        return dl4jEnabled && autoencoder != null;
    }

    public AnomalyResult detectAnomaly(Long txnId, Map<String, Object> features) {
        if (!isEnabled()) {
            throw new IllegalStateException("DL4J anomaly detection is not enabled with a trained model");
        }
        long startTime = System.currentTimeMillis();
        try {
            INDArray input = featuresToNDArray(features);
            INDArray reconstruction = autoencoder.output(input);
            double anomalyScore = computeReconstructionError(input, reconstruction);
            boolean anomaly = anomalyScore > anomalyThreshold;
            AnomalyResult result = new AnomalyResult(
                    txnId,
                    anomalyScore,
                    anomaly,
                    anomaly ? "ANOMALY_DETECTED" : "NORMAL",
                    System.currentTimeMillis() - startTime);
            cacheAnomalyResult(txnId, result);
            return result;
        } catch (Exception failure) {
            throw new IllegalStateException("Error detecting anomaly for transaction " + txnId, failure);
        }
    }

    private INDArray featuresToNDArray(Map<String, Object> features) {
        double[] values = new double[INPUT_SIZE];
        values[0] = normalize(features.get("amount"), 0, 100000);
        values[1] = normalize(features.get("log_amount"), 0, 12);
        values[2] = normalize(features.get("txn_hour_of_day"), 0, 24);
        values[3] = normalize(features.get("txn_day_of_week"), 1, 7);
        values[4] = normalize(features.get("merchant_txn_count_1h"), 0, 100);
        values[5] = normalize(features.get("merchant_txn_amount_sum_24h"), 0, 1000000);
        values[6] = normalize(features.get("pan_txn_count_1h"), 0, 50);
        values[7] = normalize(features.get("pan_txn_amount_sum_7d"), 0, 50000);
        values[8] = normalize(features.get("distinct_terminals_last_30d_for_pan"), 0, 20);
        values[9] = normalize(features.get("avg_amount_by_pan_30d"), 0, 10000);
        values[10] = normalize(features.get("time_since_last_txn_for_pan_minutes"), 0, 10080);
        values[11] = normalize(features.get("zscore_amount_vs_pan_history"), -5, 5);
        values[12] = normalize(features.get("pageRank"), 0, 1);
        values[13] = normalize(features.get("betweenness"), 0, 1);
        values[14] = normalize(features.get("connectionCount"), 0, 100);
        values[15] = normalize(features.get("ml_score"), 0, 1);
        values[16] = boolToDouble(features.get("is_chip_present"));
        values[17] = boolToDouble(features.get("is_contactless"));
        values[18] = normalize(features.get("cvm_method"), 0, 10);
        Object communityId = features.get("communityId");
        values[19] = communityId instanceof Number number ? (number.longValue() % 100) / 100.0 : 0.0;
        return Nd4j.create(values).reshape(1, INPUT_SIZE);
    }

    private double computeReconstructionError(INDArray input, INDArray reconstruction) {
        INDArray difference = input.sub(reconstruction);
        return difference.mul(difference).meanNumber().doubleValue();
    }

    private double normalize(Object value, double min, double max) {
        if (!(value instanceof Number number)) return 0.0;
        return Math.max(0.0, Math.min(1.0, (number.doubleValue() - min) / (max - min)));
    }

    private double boolToDouble(Object value) {
        return Boolean.TRUE.equals(value) ? 1.0 : 0.0;
    }

    private void cacheAnomalyResult(Long txnId, AnomalyResult result) {
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("anomalyScore", result.getAnomalyScore());
        cacheData.put("isAnomaly", result.isAnomaly());
        cacheData.put("reason", result.getReason());
        cacheData.put("latencyMs", result.getLatencyMs());
        cacheData.put("updatedAt", System.currentTimeMillis());
        redisTemplate.opsForValue().set("graph:metrics:anomaly_" + txnId, cacheData, Duration.ofHours(1));
    }

    public static class AnomalyResult {
        private final Long txnId;
        private final double anomalyScore;
        private final boolean anomaly;
        private final String reason;
        private final long latencyMs;

        public AnomalyResult(Long txnId, double anomalyScore, boolean anomaly, String reason) {
            this(txnId, anomalyScore, anomaly, reason, 0L);
        }

        public AnomalyResult(Long txnId, double anomalyScore, boolean anomaly, String reason, long latencyMs) {
            this.txnId = txnId;
            this.anomalyScore = anomalyScore;
            this.anomaly = anomaly;
            this.reason = reason;
            this.latencyMs = latencyMs;
        }

        public Long getTxnId() { return txnId; }
        public double getAnomalyScore() { return anomalyScore; }
        public boolean isAnomaly() { return anomaly; }
        public String getReason() { return reason; }
        public long getLatencyMs() { return latencyMs; }
    }
}
