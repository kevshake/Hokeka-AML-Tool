package com.posgateway.aml.service.onprem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import com.posgateway.aml.dto.onprem.OnPremLeaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Persists the last successful lease to a local JSON file so an on-prem restart within the
 * lease window does not require an immediate upstream round-trip (token is still verified when
 * the signing secret is configured).
 */
@Component
@ConditionalOnProperty(name = "hokeka.auth.enabled", havingValue = "true")
public class OnPremLeaseStore {

    private static final Logger log = LoggerFactory.getLogger(OnPremLeaseStore.class);

    private final Path storePath;
    private final ObjectMapper objectMapper;

    public OnPremLeaseStore(HokekaAuthProperties properties, ObjectMapper objectMapper) {
        this.storePath = Path.of(properties.getLeaseStorePath());
        this.objectMapper = objectMapper;
    }

    public void save(OnPremLeaseResponse lease) {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            StoredLease stored = new StoredLease(
                    lease.instanceId(),
                    lease.pspId(),
                    lease.validUntil(),
                    lease.nextCheckAt(),
                    lease.approvedDays(),
                    lease.checkIntervalDays(),
                    lease.leaseToken(),
                    Instant.now());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), stored);
        } catch (IOException e) {
            log.error("Failed to persist on-prem lease to {}: {}", storePath, e.getMessage());
        }
    }

    public StoredLease load() {
        if (!Files.isRegularFile(storePath)) {
            return null;
        }
        try {
            return objectMapper.readValue(storePath.toFile(), StoredLease.class);
        } catch (IOException e) {
            log.warn("Could not read on-prem lease store {}: {}", storePath, e.getMessage());
            return null;
        }
    }

    public record StoredLease(
            String instanceId,
            Long pspId,
            Instant validUntil,
            Instant nextCheckAt,
            int approvedDays,
            int checkIntervalDays,
            String leaseToken,
            Instant savedAt
    ) {
    }
}
