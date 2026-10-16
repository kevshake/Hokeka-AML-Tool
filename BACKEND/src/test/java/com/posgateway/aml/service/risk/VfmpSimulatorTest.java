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

class VfmpSimulatorTest {

    /** Verifies missing config keeps legacy critical literal. */
    @Test
    void missingConfigKeepsLegacyCriticalLiteral() {
        SchemeProgramThresholdRepository repository = mock(SchemeProgramThresholdRepository.class);
        when(repository.findEffectiveRows(eq("VFMP"), any())).thenReturn(java.util.List.of());

        VfmpSimulator.VfmpResult result = new VfmpSimulator(repository)
                .evaluate(metrics(0.015, 500));

        assertEquals(VfmpSimulator.VfmpStage.CRITICAL, result.getStage());
    }

    /** Verifies present row changes stage without code change. */
    @Test
    void presentRowChangesStageWithoutCodeChange() {
        SchemeProgramThresholdRepository repository = mock(SchemeProgramThresholdRepository.class);
        SchemeProgramThreshold row = new SchemeProgramThreshold();
        row.setProgram("VFMP");
        row.setCriticalRate(0.02);
        row.setCriticalVolume(500L);
        row.setHighRate(0.009);
        row.setHighVolume(100L);
        row.setWarningRate(0.006);
        row.setWarningVolume(75L);
        row.setWatchRate(0.004);
        row.setWatchVolume(50L);
        row.setEffectiveFrom(OffsetDateTime.now().minusDays(1));
        when(repository.findEffectiveRows(eq("VFMP"), any())).thenReturn(java.util.List.of(row));

        VfmpSimulator.VfmpResult result = new VfmpSimulator(repository)
                .evaluate(metrics(0.015, 500));

        assertEquals(VfmpSimulator.VfmpStage.HIGH_RISK, result.getStage());
    }

    /** Builds test data for metrics. */
    private static MerchantMetrics metrics(double fraudRate, long fraudTx) {
        long totalTx = fraudRate == 0 ? 0 : Math.round(fraudTx / fraudRate);
        return new MerchantMetrics(totalTx, fraudTx, 0, 0, 0);
    }
}
