package com.hokeka.aml.controller;

import com.hokeka.aml.service.AmlCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight platform health for cross-service liveness probes.
 * Full path: GET /internal/v1/health
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalHealthController {

    @Autowired
    private AmlCheckService amlCheckService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("aerospike", amlCheckService.isAerospikeConnected());
        return ResponseEntity.ok(body);
    }
}
