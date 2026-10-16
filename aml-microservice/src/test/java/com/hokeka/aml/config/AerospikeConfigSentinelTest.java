package com.hokeka.aml.config;

import com.aerospike.client.AerospikeClient;
import com.hokeka.aml.model.SanctionsScreenResponse;
import com.hokeka.aml.service.SanctionsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerospikeConfigSentinelTest {

    /** Verifies init failure returns disabled sentinel not null and screening stays unavailable. */
    @Test
    void initFailureReturnsDisabledSentinelNotNullAndScreeningStaysUnavailable() {
        AerospikeClient sentinel = AerospikeClients.disabled();

        assertNotNull(sentinel);

        SanctionsService service = new SanctionsService();
        ReflectionTestUtils.setField(service, "aerospikeClient", sentinel);

        SanctionsScreenResponse result = service.screenName("Jane Doe", "PERSON");
        assertEquals("UNAVAILABLE", result.getStatus());
        assertTrue(result.getMatches().isEmpty());
    }
}
