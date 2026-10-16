package com.posgateway.aml.controller.admin;

import com.posgateway.aml.service.decision.DecisionOrchestratorModeService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionOrchestratorAdminControllerTest {

    @Test
    void statusAndUpdateExposeRuntimeOrchestratorMode() {
        DecisionOrchestratorModeService service = new DecisionOrchestratorModeService("shadow");
        DecisionOrchestratorAdminController controller = new DecisionOrchestratorAdminController(service);

        assertEquals("shadow", controller.status().getBody().get("mode"));

        Map<String, Object> updated = controller.updateMode(Map.of("mode", "live")).getBody();
        assertEquals("live", updated.get("mode"));
        assertEquals("live", service.getMode());

        controller.updateMode(Map.of("mode", "shadow"));
        assertEquals("shadow", service.getMode());
    }
}
