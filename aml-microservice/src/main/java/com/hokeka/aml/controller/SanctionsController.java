package com.hokeka.aml.controller;

import com.hokeka.aml.model.SanctionsIngestRequest;
import com.hokeka.aml.model.SanctionsScreenRequest;
import com.hokeka.aml.model.SanctionsScreenResponse;
import com.hokeka.aml.service.SanctionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal sanctions API. Protected by {@code InternalAuthFilter} (which already
 * guards everything under {@code /internal/**}).
 */
@RestController
@RequestMapping("/internal/v1/sanctions")
public class SanctionsController {

    private static final Logger log = LoggerFactory.getLogger(SanctionsController.class);

    @Autowired
    private SanctionsService sanctionsService;

    /** Screen a single name. */
    @PostMapping("/screen")
    public ResponseEntity<?> screen(@RequestBody SanctionsScreenRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "name is required");
            return ResponseEntity.badRequest().body(err);
        }
        log.debug("Sanctions screen request: name='{}', type={}, pspId={}",
                request.getName(), request.getType(), request.getPspId());
        SanctionsScreenResponse resp = sanctionsService.screenName(request.getName(), request.getType());
        if ("UNAVAILABLE".equals(resp.getStatus())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(resp);
        }
        return ResponseEntity.ok(resp);
    }

    /** Bulk-ingest entities (called by the BACKEND OpenSanctions downloader). */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody SanctionsIngestRequest request) {
        int count = sanctionsService.ingest(request);
        Map<String, Object> body = new HashMap<>();
        body.put("ingested", count);
        return ResponseEntity.ok(body);
    }

    /** Cheap count for the health/metrics endpoint. */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count() {
        Map<String, Object> body = new HashMap<>();
        long count = sanctionsService.count();
        body.put("count", count);
        if (count < 0) {
            body.put("status", "UNAVAILABLE");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        body.put("status", "UP");
        return ResponseEntity.ok(body);
    }

    /** Rebuild candidate keys after a sanctions-data or matching-strategy upgrade. */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex() {
        int updated = sanctionsService.rebuildSearchIndex();
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
