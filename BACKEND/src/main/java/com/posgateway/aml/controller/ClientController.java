package com.posgateway.aml.controller;

import com.posgateway.aml.entity.Client;
import com.posgateway.aml.service.ClientRegistrationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Client Controller
 * Manages client registration and API key management
 * No authentication required for now
 */
@RestController
@RequestMapping("/clients")
public class ClientController {

    private static final Logger logger = LoggerFactory.getLogger(ClientController.class);

    private final ClientRegistrationService clientRegistrationService;

    @Autowired
    public ClientController(ClientRegistrationService clientRegistrationService) {
        this.clientRegistrationService = clientRegistrationService;
    }

    /**
     * Register a new client
     * POST /api/v1/clients/register
     * 
     * @param request Client registration request
     * @return Registered client with API key
     */
    @PostMapping("/register")
    public ResponseEntity<Client> registerClient(
            @Valid @RequestBody ClientRegistrationService.ClientRegistrationRequest request) {
        
        logger.info("Received client registration request: {}", request.getClientName());

        try {
            Client client = clientRegistrationService.registerClient(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(client);
        } catch (Exception e) {
            logger.error("Error registering client: {}", request.getClientName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get client by API key
     * GET /api/v1/clients/by-api-key/{apiKey}
     * 
     * @param apiKey API key
     * @return Client information
     */
    @GetMapping("/by-api-key/{apiKey}")
    public ResponseEntity<Client> getClientByApiKey(@PathVariable String apiKey) {
        logger.debug("Looking up client by API key");

        return clientRegistrationService.getClientByApiKey(apiKey)
            .map(client -> ResponseEntity.ok(client))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all active clients
     * GET /api/v1/clients/active
     * 
     * @return List of active clients
     */
    @GetMapping("/active")
    public ResponseEntity<List<Client>> getActiveClients() {
        logger.debug("Retrieving all active clients");

        List<Client> clients = clientRegistrationService.getAllActiveClients();
        return ResponseEntity.ok(clients);
    }

    /**
     * Get all clients
     * GET /api/v1/clients
     * 
     * @return List of all clients
     */
    @GetMapping
    public ResponseEntity<List<Client>> getAllClients() {
        logger.debug("Retrieving all clients");

        List<Client> clients = clientRegistrationService.getAllClients();
        return ResponseEntity.ok(clients);
    }

    /**
     * Update client status
     * PUT /api/v1/clients/{clientId}/status
     * 
     * @param clientId Client ID
     * @param status New status (ACTIVE, INACTIVE, SUSPENDED)
     * @return Updated client
     */
    @PutMapping("/{clientId}/status")
    public ResponseEntity<Client> updateClientStatus(
            @PathVariable Long clientId,
            @RequestParam String status) {
        
        logger.info("Updating client status: clientId={}, status={}", clientId, status);

        // Validate status early - use switch for better performance
        if (status == null || status.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        // Normalize status to uppercase for consistent comparison
        String normalizedStatus = status.toUpperCase();
        
        try {
            Client client = clientRegistrationService.updateClientStatus(clientId, normalizedStatus);
            return ResponseEntity.ok(client);
        } catch (IllegalArgumentException e) {
            logger.warn("Client not found: {}", clientId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error updating client status: {}", clientId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     * GET /api/v1/clients/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Client Service is running");
    }
}

