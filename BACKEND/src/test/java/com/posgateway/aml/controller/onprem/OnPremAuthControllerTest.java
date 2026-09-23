package com.posgateway.aml.controller.onprem;

import com.posgateway.aml.config.onprem.OnPremLeaseDeprecation;
import com.posgateway.aml.dto.onprem.OnPremLeaseRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnPremAuthControllerTest {

    private final OnPremAuthController controller = new OnPremAuthController();

    @Test
    void leaseEndpointReturnsGone() {
        var response = controller.lease(new OnPremLeaseRequest("client", "secret", "inst", null, null));
        assertEquals(410, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains(OnPremLeaseDeprecation.CODE));
    }
}
