package com.posgateway.aml.controller.admin;

import com.posgateway.aml.service.decision.DecisionOrchestratorModeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operator-only REST surface to read and toggle {@code hokeka.decision.orchestrator} at runtime.
 */
@RestController
@RequestMapping("/admin/decision-orchestrator")
@PreAuthorize("hasAuthority('CONFIGURE_SYSTEM')")
public class DecisionOrchestratorAdminController {

    private final DecisionOrchestratorModeService modeService;

    public DecisionOrchestratorAdminController(DecisionOrchestratorModeService modeService) {
        this.modeService = modeService;
    }

    /** Returns the active decision orchestrator mode (legacy, shadow, or live). */
    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", modeService.getMode());
        body.put("allowed", new String[] { "legacy", "shadow", "live" });
        return ResponseEntity.ok(body);
    }

    /** Applies a new orchestrator mode immediately for subsequent transaction decisions. */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateMode(@RequestBody Map<String, String> body) {
        String next = body != null ? body.get("mode") : null;
        String applied = modeService.setMode(next);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", applied);
        return ResponseEntity.ok(out);
    }
}
