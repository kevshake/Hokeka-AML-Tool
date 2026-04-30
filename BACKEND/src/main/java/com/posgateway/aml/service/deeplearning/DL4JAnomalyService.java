package com.posgateway.aml.service.deeplearning;

import com.posgateway.aml.service.graph.AerospikeGraphCacheService;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * DL4J Deep Anomaly Detection Service.
 * 
 * Uses an autoencoder neural network to detect transaction anomalies.
 * The autoencoder learns normal transaction patterns; anomalies are
 * detected by high reconstruction error.
 * 
 * Architecture: Input → Encoder (compress) → Bottleneck → Decoder (reconstruct)
 * → Output
 * Anomaly Score = Reconstruction Error (MSE between input and output)
 * 
 * Results cached in Aerospike for fast retrieval.
 */
@Service
public class DL4JAnomalyService {

    private static final Logger logger = LoggerFactory.getLogger(DL4JAnomalyService.class);

    // Feature dimension (number of input features)
    private static final int INPUT_SIZE = 20;
    // Bottleneck dimension (compressed representation)
    private static final int ENCODING_SIZE = 8;
    // Hidden layer sizes
    private static final int HIDDEN_SIZE = 14;

    private final AerospikeGraphCacheService aerospikeCache;
    private MultiLayerNetwork autoencoder;

    @Value("${dl4j.anomaly.threshold:0.5}")
    private double anomalyThreshold;

    @Value("${dl4j.enabled:true}")
    private boolean dl4jEnabled;

    @Autowired
    public DL4JAnomalyService(@Autowired(required = false) AerospikeGraphCacheService aerospikeCache) {
        this.aerospikeCache = aerospikeCache;
    }

    @PostConstruct
    public void initializeModel() {
        if (!dl4jEnabled) {
            logger.info("DL4J Anomaly Detection is disabled");
            return;
        }

        logger.info("Initializing DL4J Autoencoder for anomaly detection...");
        try {
            // Build autoencoder configuration
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(42)
                    .updater(new Adam(0.001))
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    // Encoder layers
                    .layer(0, new DenseLayer.Builder()
                            .nIn(INPUT_SIZE)
                            .nOut(HIDDEN_SIZE)
                            .activation(Activation.RELU)
                            .build())
                    .layer(1, new DenseLayer.Builder()
                            .nIn(HIDDEN_SIZE)
                            .nOut(ENCODING_SIZE)
                            .activation(Activation.RELU)
                            .build())
                    // Decoder layers
                    .layer(2, new DenseLayer.Builder()
                            .nIn(ENCODING_SIZE)
                            .nOut(HIDDEN_SIZE)
                            .activation(Activation.RELU)
                            .build())
                    .layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                            .nIn(HIDDEN_SIZE)
                            .nOut(INPUT_SIZE)
                            .activation(Activation.SIGMOID)
                            .build())
                    .build();

            autoencoder = new MultiLayerNetwork(conf);
            autoencoder.init();

            logger.info("DL4J Autoencoder initialized: {} → {} → {} → {} → {}",
                    INPUT_SIZE, HIDDEN_SIZE, ENCODING_SIZE, HIDDEN_SIZE, INPUT_SIZE);

        } catch (Exception e) {
            logger.error("Error initializing DL4J Autoencoder: {}", e.getMessage(), e);
            dl4jEnabled = false;
        }
    }

    /**
     * Compute anomaly score for a transaction.
     * 
     * @param txnId    Transaction ID
     * @param features Feature map from FeatureExtractionService
     * @return Anomaly detection result with score and flag
     */
    public AnomalyResult detectAnomaly(Long txnId, Map<String, Object> features) {
        if (!dl4jEnabled || autoencoder == null) {
            logger.debug("DL4J disabled, returning default anomaly score for txn {}", txnId);
            return new AnomalyResult(txnId, 0.0, false, "DL4J_DISABLED");
        }

        long startTime = System.currentTimeMillis();

        try {
            // Convert features to INDArray
            INDArray input = featuresToNDArray(features);

            // Forward pass through autoencoder
            INDArray reconstruction = autoencoder.output(input);

            // Compute reconstruction error (anomaly score)
            double anomalyScore = computeReconstructionError(input, reconstruction);

            // Determine if anomaly
            boolean isAnomaly = anomalyScore > anomalyThreshold;

            long latencyMs = System.currentTimeMillis() - startTime;

            // Create result
            AnomalyResult result = new AnomalyResult(
                    txnId,
                    anomalyScore,
                    isAnomaly,
                    isAnomaly ? "ANOMALY_DETECTED" : "NORMAL",
                    latencyMs);

            // Cache in Aerospike
            if (aerospikeCache != null) {
                cacheAnomalyResult(txnId, result);
            }

            logger.debug("Anomaly detection for txn {}: score={}, isAnomaly={}, latency={}ms",
                    txnId, String.format("%.4f", anomalyScore), isAnomaly, latencyMs);

            return result;

        } catch (Exception e) {
            logger.error("Error detecting anomaly for txn {}: {}", txnId, e.getMessage());
            return new AnomalyResult(txnId, 0.0, false, "ERROR: " + e.getMessage());
        }
    }

    /**
     * Convert feature map to ND4J array.
     * Normalizes numeric features to [0, 1] range.
     */
    private INDArray featuresToNDArray(Map<String, Object> features) {
        double[] featureArray = new double[INPUT_SIZE];

        // Map key features to array positions
        featureArray[0] = normalize(features.get("amount"), 0, 100000);
        featureArray[1] = normalize(features.get("log_amount"), 0, 12);
        featureArray[2] = normalize(features.get("txn_hour_of_day"), 0, 24);
        featureArray[3] = normalize(features.get("txn_day_of_week"), 1, 7);
        featureArray[4] = normalize(features.get("merchant_txn_count_1h"), 0, 100);
        featureArray[5] = normalize(features.get("merchant_txn_amount_sum_24h"), 0, 1000000);
        featureArray[6] = normalize(features.get("pan_txn_count_1h"), 0, 50);
        featureArray[7] = normalize(features.get("pan_txn_amount_sum_7d"), 0, 50000);
        featureArray[8] = normalize(features.get("distinct_terminals_last_30d_for_pan"), 0, 20);
        featureArray[9] = normalize(features.get("avg_amount_by_pan_30d"), 0, 10000);
        featureArray[10] = normalize(features.get("time_since_last_txn_for_pan_minutes"), 0, 10080);
        featureArray[11] = normalize(features.get("zscore_amount_vs_pan_history"), -5, 5);

        // Graph features from Neo4j GDS
        featureArray[12] = normalize(features.get("pageRank"), 0, 1);
        featureArray[13] = normalize(features.get("betweenness"), 0, 1);
        featureArray[14] = normalize(features.get("connectionCount"), 0, 100);

        // ML score from XGBoost
        featureArray[15] = normalize(features.get("ml_score"), 0, 1);

        // Boolean/categorical features
        featureArray[16] = boolToDouble(features.get("is_chip_present"));
        featureArray[17] = boolToDouble(features.get("is_contactless"));
        featureArray[18] = normalize(features.get("cvm_method"), 0, 10);

        // Community ID (modulo for normalization)
        Object communityId = features.get("communityId");
        featureArray[19] = communityId != null ? (((Number) communityId).longValue() % 100) / 100.0 : 0.0;

        return Nd4j.create(featureArray).reshape(1, INPUT_SIZE);
    }

    /**
     * Compute reconstruction error (Mean Squared Error).
     */
    private double computeReconstructionError(INDArray input, INDArray reconstruction) {
        INDArray diff = input.sub(reconstruction);
        INDArray squared = diff.mul(diff);
        return squared.meanNumber().doubleValue();
    }

    private double normalize(Object value, double min, double max) {
        if (value == null)
            return 0.0;
        double v = value instanceof Number ? ((Number) value).doubleValue() : 0.0;
        return Math.max(0.0, Math.min(1.0, (v - min) / (max - min)));
    }

    private double boolToDouble(Object value) {
        if (value == null)
            return 0.0;
        if (value instanceof Boolean)
            return (Boolean) value ? 1.0 : 0.0;
        return 0.0;
    }

    /**
     * Cache anomaly result in Aerospike.
     */
    private void cacheAnomalyResult(Long txnId, AnomalyResult result) {
        try {
            Map<String, Object> cacheData = new HashMap<>();
            cacheData.put("anomalyScore", result.getAnomalyScore());
            cacheData.put("isAnomaly", result.isAnomaly() ? 1L : 0L);
            cacheData.put("reason", result.getReason());
            cacheData.put("latencyMs", result.getLatencyMs());

            // Use existing cache method with graph metrics set
            aerospikeCache.cacheGraphMetrics("anomaly_" + txnId, cacheData);
        } catch (Exception e) {
            logger.warn("Error caching anomaly result for txn {}: {}", txnId, e.getMessage());
        }
    }

    /**
     * Anomaly Detection Result.
     */
    public static class AnomalyResult {
        private final Long txnId;
        private final double anomalyScore;
        private final boolean isAnomaly;
        private final String reason;
        private final long latencyMs;

        public AnomalyResult(Long txnId, double anomalyScore, boolean isAnomaly, String reason) {
            this(txnId, anomalyScore, isAnomaly, reason, 0L);
        }

        public AnomalyResult(Long txnId, double anomalyScore, boolean isAnomaly, String reason, long latencyMs) {
            this.txnId = txnId;
            this.anomalyScore = anomalyScore;
            this.isAnomaly = isAnomaly;
            this.reason = reason;
            this.latencyMs = latencyMs;
        }

        public Long getTxnId() {
            return txnId;
        }

        public double getAnomalyScore() {
            return anomalyScore;
        }

        public boolean isAnomaly() {
            return isAnomaly;
        }

        public String getReason() {
            return reason;
        }

        public long getLatencyMs() {
            return latencyMs;
        }

        @Override
        public String toString() {
            return String.format("AnomalyResult[txn=%d, score=%.4f, anomaly=%s, latency=%dms]",
                    txnId, anomalyScore, isAnomaly, latencyMs);
        }
    }
}
