package com.posgateway.aml.service.risk;

import com.posgateway.aml.repository.risk.MccRiskScoreRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MCC risk scores — configurable via {@code risk.mcc} in application config,
 * overridden by an effective {@code mcc_risk_scores} row when present.
 */
@Configuration
@ConfigurationProperties(prefix = "risk.mcc")
public class MccRiskConfig {

    private final MccRiskScoreRepository mccRiskScoreRepository;

    private Map<String, Double> scores = new HashMap<>();

    /** Default mapping when config and DB are absent. */
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

    /** Creates the resolver that overlays effective database scores on configuration defaults. */
    public MccRiskConfig(MccRiskScoreRepository mccRiskScoreRepository) {
        this.mccRiskScoreRepository = mccRiskScoreRepository;
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
        if (mccRiskScoreRepository != null) {
            var rows = mccRiskScoreRepository.findEffectiveRows(mcc, OffsetDateTime.now());
            var row = rows == null ? java.util.Optional.<com.posgateway.aml.entity.risk.MccRiskScore>empty()
                    : rows.stream().findFirst();
            if (row.isPresent() && row.get().getRiskScore() != null) {
                return row.get().getRiskScore();
            }
        }
        return scores.getOrDefault(mcc, 50.0);
    }
}
