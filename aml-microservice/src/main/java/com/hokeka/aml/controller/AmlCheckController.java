package com.hokeka.aml.controller;

import com.hokeka.aml.model.AmlResult;
import com.hokeka.aml.model.TransactionRequest;
import com.hokeka.aml.service.AmlCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/aml")
public class AmlCheckController {

    @Autowired
    private AmlCheckService amlCheckService;

    /**
     * Score a transaction. PSP-namespaced via {@code pspId} (mandatory).
     * Full path: POST /internal/v1/aml/score
     */
    @PostMapping("/score")
    public ResponseEntity<?> score(@RequestBody TransactionRequest request) {
        if (request == null || request.getPspId() == null) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "pspId is required");
            return ResponseEntity.badRequest().body(err);
        }
        AmlResult result = amlCheckService.check(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Lightweight health endpoint (always open, used by docker-compose healthcheck).
     * Full path: GET /internal/v1/aml/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("aerospike", amlCheckService.isAerospikeConnected());
        body.put("service", "aml-microservice");
        return ResponseEntity.ok(body);
    }
}
