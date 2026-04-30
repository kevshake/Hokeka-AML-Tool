package com.posgateway.aml.controller;

import com.posgateway.aml.service.AerospikeConnectionManager;
import com.posgateway.aml.service.AerospikeConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Aerospike Connection Controller
 * Provides endpoints to monitor and manage Aerospike connection status
 */
@RestController
@RequestMapping("/aerospike")
public class AerospikeConnectionController {

    private final AerospikeConnectionManager connectionManager;
    private final AerospikeConnectionService connectionService;

    public AerospikeConnectionController(AerospikeConnectionManager connectionManager,
                                        AerospikeConnectionService connectionService) {
        this.connectionManager = connectionManager;
        this.connectionService = connectionService;
    }

    /**
     * Get Aerospike connection status
     * GET /api/v1/aerospike/status
     */
    @GetMapping("/status")
    public ResponseEntity<AerospikeConnectionService.ConnectionStatus> getConnectionStatus() {
        AerospikeConnectionService.ConnectionStatus status = connectionService.getConnectionStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Check if Aerospike is enabled
     * GET /api/v1/aerospike/enabled
     */
    @GetMapping("/enabled")
    public ResponseEntity<Map<String, Object>> isEnabled() {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", connectionService.isEnabled());
        response.put("connected", connectionService.isConnected());
        return ResponseEntity.ok(response);
    }

    /**
     * Force connection check and reconnect if needed
     * POST /api/v1/aerospike/reconnect
     */
    @PostMapping("/reconnect")
    public ResponseEntity<Map<String, Object>> reconnect() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            connectionManager.ensureConnection();
            boolean connected = connectionService.isConnected();
            
            response.put("success", connected);
            response.put("message", connected ? "Connection verified" : "Failed to connect");
            response.put("connected", connected);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            response.put("connected", false);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get detailed connection information
     * GET /api/v1/aerospike/info
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getConnectionInfo() {
        Map<String, Object> info = new HashMap<>();
        
        AerospikeConnectionService.ConnectionStatus status = connectionService.getConnectionStatus();
        info.put("status", status);
        info.put("namespace", connectionService.getNamespace());
        info.put("managerActive", connectionManager.isActive());
        info.put("ready", status.isEnabled() && status.isConnected());
        info.put("version", "9.2.3 (astools detected)");
        
        return ResponseEntity.ok(info);
    }
}

