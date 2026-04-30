package com.posgateway.aml.service;

import com.posgateway.aml.entity.Client;
import com.posgateway.aml.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Client Registration Service
 * Manages client registration and API key generation
 */
@Service
public class ClientRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(ClientRegistrationService.class);

    private final ClientRepository clientRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public ClientRegistrationService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    /**
     * Register a new client
     * 
     * @param request Client registration request
     * @return Registered client with generated API key
     */
    @Transactional
    public Client registerClient(ClientRegistrationRequest request) {
        logger.info("Registering new client: {}", request.getClientName());

        // Generate API key
        String apiKey = generateApiKey();

        // Ensure API key is unique
        while (clientRepository.existsByApiKey(apiKey)) {
            apiKey = generateApiKey();
        }

        Client client = new Client();
        client.setClientName(request.getClientName());
        client.setApiKey(apiKey);
        client.setContactEmail(request.getContactEmail());
        client.setContactPhone(request.getContactPhone());
        client.setStatus("ACTIVE");
        client.setDescription(request.getDescription());

        Client saved = clientRepository.save(client);
        logger.info("Client registered successfully: clientId={}, apiKey={}", 
            saved.getClientId(), saved.getApiKey());

        return saved;
    }

    /**
     * Get client by API key
     */
    public Optional<Client> getClientByApiKey(String apiKey) {
        // Early return for null/empty API key
        if (apiKey == null || apiKey.isEmpty()) {
            return Optional.empty();
        }
        
        Optional<Client> client = clientRepository.findByApiKey(apiKey);
        if (client.isPresent()) {
            // Update last accessed timestamp
            Client clientEntity = client.get();
            clientEntity.setLastAccessedAt(LocalDateTime.now());
            clientRepository.save(clientEntity);
        }
        return client;
    }

    /**
     * Get all active clients
     */
    public List<Client> getAllActiveClients() {
        return clientRepository.findByStatusOrderByClientNameAsc("ACTIVE");
    }

    /**
     * Get all clients
     */
    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }

    /**
     * Update client status
     */
    @Transactional
    public Client updateClientStatus(Long clientId, String status) {
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
        
        client.setStatus(status);
        Client updated = clientRepository.save(client);
        logger.info("Updated client status: clientId={}, status={}", clientId, status);
        return updated;
    }

    /**
     * Generate API key (32 bytes, Base64 encoded)
     */
    private String generateApiKey() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Client Registration Request DTO
     */
    public static class ClientRegistrationRequest {
        private String clientName;
        private String contactEmail;
        private String contactPhone;
        private String description;

        // Getters and Setters
        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = clientName;
        }

        public String getContactEmail() {
            return contactEmail;
        }

        public void setContactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
        }

        public String getContactPhone() {
            return contactPhone;
        }

        public void setContactPhone(String contactPhone) {
            this.contactPhone = contactPhone;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}

