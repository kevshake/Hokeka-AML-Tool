package com.posgateway.aml.service.download;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.service.sanctions.NameMatchingService;
import com.posgateway.aml.service.sanctions.WatchlistUpdateTrackingService;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sanctions List Download Service
 * Downloads sanctions data from OpenSanctions daily and loads into Aerospike
 * Improved with Retry logic and Stale Data Monitoring
 */
// @RequiredArgsConstructor removed
@Service
public class SanctionsListDownloadService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SanctionsListDownloadService.class);

    private final NameMatchingService nameMatchingService;
    private final com.posgateway.aml.service.AerospikeConnectionService aerospikeService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final WatchlistUpdateTrackingService watchlistUpdateTrackingService;

    @Autowired
    public SanctionsListDownloadService(NameMatchingService nameMatchingService,
            com.posgateway.aml.service.AerospikeConnectionService aerospikeService, ObjectMapper objectMapper,
            RestTemplate restTemplate, WatchlistUpdateTrackingService watchlistUpdateTrackingService) {
        this.nameMatchingService = nameMatchingService;
        this.aerospikeService = aerospikeService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.watchlistUpdateTrackingService = watchlistUpdateTrackingService;
    }

    @Value("${sanctions.download.enabled:true}")
    private boolean downloadEnabled;

    @Value("${sanctions.download.temp.dir:C:/temp/sanctions}")
    private String tempDirectory;

    @Value("${sanctions.opensanctions.url}")
    private String opensanctionsUrl;

    @Value("${sanctions.opensanctions.metadata.url}")
    private String metadataUrl;

    // Track last successful update for monitoring
    private final AtomicReference<LocalDateTime> lastSuccessfulUpdate = new AtomicReference<>(
            LocalDateTime.now().minusDays(1)); // Start stale to force check

    /**
     * Scheduled download job - runs daily at 2:00 AM
     * Retries up to 3 times to handle network glitches
     */
    @Scheduled(cron = "${sanctions.download.cron:0 0 2 * * *}")
    @Retry(name = "sanctionsDownload")
    public void performScheduledDownload() {
        if (!downloadEnabled) {
            log.info("Sanctions download is disabled, skipping");
            return;
        }

        log.info("Starting scheduled sanctions list download...");

        try {
            downloadAndProcessSanctions();
            lastSuccessfulUpdate.set(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error during scheduled download: {}", e.getMessage(), e);
            throw e; // Rethrow to trigger Retry
        }
    }

    /**
     * Watchdog job - runs hourly to check for stale data
     * Alerts if data is > 26 hours old (giving 2h buffer after 24h cycle)
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void checkStaleData() {
        if (!downloadEnabled)
            return;

        LocalDateTime lastUpdate = lastSuccessfulUpdate.get();
        long hoursSinceUpdate = ChronoUnit.HOURS.between(lastUpdate, LocalDateTime.now());

        if (hoursSinceUpdate > 26) {
            log.error("CRITICAL: Sanctions data is STALE! Last update was {} hours ago. Check download service.",
                    hoursSinceUpdate);
            // In a real system, this would trigger PagerDuty/Slack alert
        }
    }

    /**
     * Download and process sanctions data
     */
    public void downloadAndProcessSanctions() {
        log.info("Downloading sanctions data from OpenSanctions...");

        try {
            // Step 1: Check metadata for version
            String currentVersion = checkMetadataVersion();
            log.info("Latest sanctions data version: {}", currentVersion);

            // Step 2: Create temp directory
            Path tempDir = Paths.get(tempDirectory);
            Files.createDirectories(tempDir);

            // Step 3: Download file
            Path downloadedFile = downloadFile(opensanctionsUrl, tempDir);
            log.info("Downloaded file: {}", downloadedFile);

            // Step 4: Process file and load to Aerospike
            int recordsProcessed = processAndLoadToAerospike(downloadedFile, currentVersion);

            log.info("Successfully processed {} sanctions records", recordsProcessed);

            // Step 5: Record watchlist update in tracking service (data is stored in
            // Aerospike)
            try {
                watchlistUpdateTrackingService.recordUpdate(
                        "OPENSANCTIONS",
                        "SANCTIONS",
                        java.time.LocalDate.now(),
                        (long) recordsProcessed,
                        opensanctionsUrl,
                        currentVersion // Use version as checksum
                );
                log.info("Recorded watchlist update: {} records loaded to Aerospike", recordsProcessed);
            } catch (Exception e) {
                log.warn("Failed to record watchlist update: {}", e.getMessage());
                // Don't fail the entire process if tracking fails
            }

            // Step 5: Delete temp file after successful processing
            Files.deleteIfExists(downloadedFile);
            log.info("Cleaned up temporary file");

        } catch (Exception e) {
            log.error("Failed to download and process sanctions: {}", e.getMessage(), e);
            throw new RuntimeException("Sanctions download failed", e);
        }
    }

    /**
     * Check metadata for current version
     */
    private String checkMetadataVersion() {
        try {
            String metadata = restTemplate.getForObject(metadataUrl, String.class);
            JsonNode metadataJson = objectMapper.readTree(metadata);
            return metadataJson.get("version").asText();
        } catch (Exception e) {
            log.warn("Could not fetch metadata, using timestamp as version");
            return LocalDateTime.now().toString();
        }
    }

    /**
     * Download file to local disk
     */
    private Path downloadFile(String url, Path targetDir) throws IOException {
        log.info("Downloading from: {}", url);

        String fileName = "sanctions_" + System.currentTimeMillis() + ".json";
        Path targetFile = targetDir.resolve(fileName);

        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, targetFile);
        }

        return targetFile;
    }

    /**
     * Process downloaded file and load to Aerospike
     */
    private int processAndLoadToAerospike(Path filePath, String version) throws IOException {
        log.info("Processing sanctions file: {}", filePath);

        int recordCount = 0;
        List<SanctionEntity> batch = new ArrayList<>();
        int batchSize = 100;

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                try {
                    JsonNode entityJson = objectMapper.readTree(line);
                    SanctionEntity entity = parseSanctionEntity(entityJson);

                    batch.add(entity);
                    recordCount++;

                    // Process in batches
                    if (batch.size() >= batchSize) {
                        insertBatchToAerospike(batch);
                        batch.clear();
                    }

                } catch (Exception e) {
                    log.warn("Failed to parse entity: {}", e.getMessage());
                }
            }

            // Process remaining batch
            if (!batch.isEmpty()) {
                insertBatchToAerospike(batch);
            }
        }

        log.info("Processed {} sanctions entities", recordCount);
        return recordCount;
    }

    /**
     * Parse JSON into SanctionEntity
     */
    private SanctionEntity parseSanctionEntity(JsonNode json) {
        String fullName = json.has("name") ? json.get("name").asText() : "";
        String entityType = json.has("schema") ? json.get("schema").asText() : "UNKNOWN";

        // Generate phonetic codes
        String phoneticCode = nameMatchingService.generatePhoneticCode(fullName);
        String altPhoneticCode = nameMatchingService.generateAlternatePhoneticCode(fullName);

        return new SanctionEntity(fullName, entityType, phoneticCode, altPhoneticCode, json.toString());
    }

    /**
     * Insert batch of entities to Aerospike
     */
    /**
     * Insert batch of entities to Aerospike
     */
    private void insertBatchToAerospike(List<SanctionEntity> batch) {
        if (batch == null || batch.isEmpty())
            return;

        com.aerospike.client.AerospikeClient client = aerospikeService.getClient();
        if (client == null) {
            log.error("Aerospike client not available for batch insertion");
            return;
        }

        try {
            com.aerospike.client.policy.WritePolicy policy = new com.aerospike.client.policy.WritePolicy();
            policy.sendKey = true;
            // policy.expiration = -1; // Never expire by default, or set retention

            for (SanctionEntity entity : batch) {
                // Key: phoneticCode (for fast lookup) + hash of name (collision avoidance)
                String keyStr = entity.getPhoneticCode() + ":" + entity.getFullName().hashCode();
                com.aerospike.client.Key key = new com.aerospike.client.Key("sanctions", "entities", keyStr);

                com.aerospike.client.Bin nameBin = new com.aerospike.client.Bin("full_name", entity.getFullName());
                com.aerospike.client.Bin typeBin = new com.aerospike.client.Bin("entity_type", entity.getEntityType());
                com.aerospike.client.Bin phoneCodeBin = new com.aerospike.client.Bin("name_metaphone",
                        entity.getPhoneticCode());
                com.aerospike.client.Bin altCodeBin = new com.aerospike.client.Bin("name_alt_metaphone",
                        entity.getAltPhoneticCode());
                com.aerospike.client.Bin rawBin = new com.aerospike.client.Bin("raw_data", entity.getRawData());
                com.aerospike.client.Bin updatedBin = new com.aerospike.client.Bin("updated_at",
                        System.currentTimeMillis());

                client.put(policy, key, nameBin, typeBin, phoneCodeBin, altCodeBin, rawBin, updatedBin);
            }

            log.debug("Inserted batch of {} entities to Aerospike", batch.size());

        } catch (com.aerospike.client.AerospikeException e) {
            log.error("Aerospike batch write error: {}", e.getMessage());
            // In production, might want to retry individual records or throw to trigger
            // global retry
        }
    }

    /**
     * Manual trigger for download
     */
    public void manualDownload() {
        log.info("Manual sanctions download triggered");
        try {
            downloadAndProcessSanctions();
            lastSuccessfulUpdate.set(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Manual download failed", e);
            throw e;
        }
    }

    private static class SanctionEntity {
        private String fullName;
        private String entityType;
        private String phoneticCode;
        private String altPhoneticCode;
        private String rawData;

        public SanctionEntity(String fullName, String entityType, String phoneticCode, String altPhoneticCode,
                String rawData) {
            this.fullName = fullName;
            this.entityType = entityType;
            this.phoneticCode = phoneticCode;
            this.altPhoneticCode = altPhoneticCode;
            this.rawData = rawData;
        }

        public String getFullName() {
            return fullName;
        }

        public String getEntityType() {
            return entityType;
        }

        public String getPhoneticCode() {
            return phoneticCode;
        }

        public String getAltPhoneticCode() {
            return altPhoneticCode;
        }

        public String getRawData() {
            return rawData;
        }
    }
}
