package com.posgateway.aml.service.risk;

import com.posgateway.aml.entity.risk.SchemeProgramThreshold;
import com.posgateway.aml.model.MerchantMetrics;
import com.posgateway.aml.repository.risk.SchemeProgramThresholdRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HecmSimulatorTest {

    @Test
    void missingConfigKeepsLegacyExcessiveLiteral() {
        SchemeProgramThresholdRepository repository = mock(SchemeProgramThresholdRepository.class);
        when(repository.findEffectiveRows(eq("HECM"), any())).thenReturn(java.util.List.of());

        HecmSimulator.HecmResult result = new HecmSimulator(repository)
                .evaluate(metrics(0.015, 300));

        assertEquals(HecmSimulator.HecmStage.EXCESSIVE, result.getStage());
    }

    @Test
    void presentRowChangesStageWithoutCodeChange() {
        SchemeProgramThresholdRepository repository = mock(SchemeProgramThresholdRepository.class);
        SchemeProgramThreshold row = new SchemeProgramThreshold();
        row.setProgram("HECM");
        row.setCriticalRate(0.02);
        row.setCriticalVolume(300L);
        row.setHighRate(0.010);
        row.setHighVolume(100L);
        row.setWatchRate(0.0065);
        row.setWatchVolume(75L);
        row.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        when(repository.findEffectiveRows(eq("HECM"), any())).thenReturn(java.util.List.of(row));

        HecmSimulator.HecmResult result = new HecmSimulator(repository)
                .evaluate(metrics(0.015, 300));

        assertEquals(HecmSimulator.HecmStage.HIGH, result.getStage());
    }

    private static MerchantMetrics metrics(double chargebackRatio, long chargebackCount) {
        long totalTx = chargebackRatio == 0 ? 0 : Math.round(chargebackCount / chargebackRatio);
        return new MerchantMetrics(totalTx, 0, chargebackCount, 0, 0);
    }
}
