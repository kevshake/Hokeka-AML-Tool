package com.posgateway.aml.service.risk;

import com.posgateway.aml.entity.risk.MccRiskScore;
import com.posgateway.aml.repository.risk.MccRiskScoreRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MccRiskConfigTest {

    @Test
    void missingRowKeepsHardcodedGamblingScore() {
        MccRiskScoreRepository repository = mock(MccRiskScoreRepository.class);
        when(repository.findEffectiveRows(eq("7995"), any())).thenReturn(java.util.List.of());

        assertEquals(90.0, new MccRiskConfig(repository).getRiskForMcc("7995"), 0.0001);
    }

    @Test
    void presentRowChangesScoreWithoutCodeChange() {
        MccRiskScoreRepository repository = mock(MccRiskScoreRepository.class);
        MccRiskScore row = new MccRiskScore();
        row.setMcc("7995");
        row.setRiskScore(20.0);
        when(repository.findEffectiveRows(eq("7995"), any())).thenReturn(java.util.List.of(row));

        assertEquals(20.0, new MccRiskConfig(repository).getRiskForMcc("7995"), 0.0001);
    }
}
