package com.posgateway.aml.service.risk;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * MCC risk scores — configurable via {@code risk.mcc} in application config.
 *
 * <p>Managed in code (properties) until the {@code mcc_risk} DB table is created.
 * Once the table exists, {@link RiskScoringService} can be updated to query it.
 *
 * <p>Example YAML:
 * <pre>{@code
 * risk:
 *   mcc:
 *     5411: 10.0    # grocery
 *     5812: 20.0    # restaurants
 *     7995: 90.0    # gambling
 * }</pre>
 */
@Configuration
@ConfigurationProperties(prefix = "risk.mcc")
public class MccRiskConfig {

    private Map<String, Double> scores = new HashMap<>();

    /** Default mapping when config is absent. */
    {
        scores.put("5411", 10.0);
        scores.put("5812", 20.0);
        scores.put("5999", 50.0);
        scores.put("6051", 75.0);
        scores.put("6211", 65.0);
        scores.put("7273", 70.0);
        scores.put("7995", 90.0);
        scores.put("9223", 85.0);
    }

    public Map<String, Double> getScores() {
        return scores;
    }

    public void setScores(Map<String, Double> scores) {
        this.scores = scores != null ? scores : new HashMap<>();
    }

    /** Look up risk score for an MCC code, or return 50.0 (neutral) if unknown. */
    public double getRiskForMcc(String mcc) {
        if (mcc == null) return 50.0;
        return scores.getOrDefault(mcc, 50.0);
    }
}