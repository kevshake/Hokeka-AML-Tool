package com.posgateway.aml.service.psp;

import com.posgateway.aml.entity.psp.BillingRate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the fix for W37-4: computeTieredCost's up_to field used to be cast directly with
 * ((Number) upToRaw).longValue(), throwing ClassCastException whenever a tier_config's up_to value
 * arrived as a JSON string (e.g. "10000") instead of a bare number -- unlike the adjacent rate
 * field in the same tier, which already parsed defensively via BigDecimal(rateRaw.toString()).
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTieredCostTest {

    private final BillingService service = new BillingService(null, null, null, null, null, null);

    private BigDecimal invokeComputeTieredCost(BillingRate rate, int count) throws Exception {
        Method m = BillingService.class.getDeclaredMethod("computeTieredCost", BillingRate.class, int.class);
        m.setAccessible(true);
        return (BigDecimal) m.invoke(service, rate, count);
    }

    @Test
    void upToAsAStringNoLongerThrowsClassCastException() throws Exception {
        // Tier config identical to the real JSONB shape, except up_to has come through as a
        // JSON string rather than a number -- exactly the malformed-but-plausible input the bug
        // report described.
        Map<String, Object> tier1 = Map.of("up_to", "10000", "rate", "0.0050");
        Map<String, Object> tier2 = new java.util.HashMap<>();
        tier2.put("up_to", null); // unlimited last tier
        tier2.put("rate", "0.0030");

        BillingRate rate = BillingRate.builder()
                .baseRate(new BigDecimal("0.0100"))
                .tierConfig(Map.of("tiers", List.of(tier1, tier2)))
                .build();

        BigDecimal result = assertDoesNotThrow(() -> invokeComputeTieredCost(rate, 5000));

        // 5000 requests, entirely within the first tier's up_to=10000 cap -> 5000 * 0.0050
        assertEquals(0, new BigDecimal("25.0000").compareTo(result));
    }

    @Test
    void upToAsANumberStillWorksAsBefore() throws Exception {
        Map<String, Object> tier1 = Map.of("up_to", 100, "rate", "0.0050");
        Map<String, Object> tier2 = new java.util.HashMap<>();
        tier2.put("up_to", null);
        tier2.put("rate", "0.0030");

        BillingRate rate = BillingRate.builder()
                .baseRate(new BigDecimal("0.0100"))
                .tierConfig(Map.of("tiers", List.of(tier1, tier2)))
                .build();

        BigDecimal result = invokeComputeTieredCost(rate, 150);

        // First 100 at 0.0050 = 0.50, remaining 50 at 0.0030 = 0.15 -> 0.65
        assertEquals(0, new BigDecimal("0.6500").compareTo(result));
    }

    @Test
    void malformedUpToStringSkipsThatTierInsteadOfThrowing() throws Exception {
        Map<String, Object> tier1 = Map.of("up_to", "not-a-number", "rate", "0.0050");

        BillingRate rate = BillingRate.builder()
                .baseRate(new BigDecimal("0.0200"))
                .tierConfig(Map.of("tiers", List.of(tier1)))
                .build();

        BigDecimal result = assertDoesNotThrow(() -> invokeComputeTieredCost(rate, 10));

        // The malformed-up_to tier is skipped entirely (no slice consumed), so the full count
        // falls through to the "remainder" path, which bills at the last tier's own rate field
        // (still parseable: "0.0050") rather than the base rate: 10 * 0.0050 = 0.0500.
        assertEquals(0, new BigDecimal("0.0500").compareTo(result));
    }
}
