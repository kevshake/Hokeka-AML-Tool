package com.posgateway.aml.service.download;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.client.aml.AmlMicroserviceProperties;
import com.posgateway.aml.service.sanctions.WatchlistUpdateTrackingService;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Downloads OpenSanctions data daily and pushes the parsed entities into the
 * aml-microservice via {@code POST /internal/v1/sanctions/ingest}.
 *
 * <p>BACKEND no longer holds Aerospike directly — sanctions data lives in the
 * microservice. This class is the producer side of that pipeline.
 */
@Service
public class SanctionsListDownloadService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SanctionsListDownloadService.class);
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final int DEFAULT_BATCH_SIZE = 500;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final WatchlistUpdateTrackingService watchlistUpdateTrackingService;
    private final AmlMicroserviceProperties amlMicroserviceProperties;

    @Autowired
    public SanctionsListDownloadService(ObjectMapper objectMapper,
                                        RestTemplate restTemplate,
                                        WatchlistUpdateTrackingService watchlistUpdateTrackingService,
                                        AmlMicroserviceProperties amlMicroserviceProperties) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.watchlistUpdateTrackingService = watchlistUpdateTrackingService;
        this.amlMicroserviceProperties = amlMicroserviceProperties;
    }

    @Value("${sanctions.download.enabled:false}")
    private boolean downloadEnabled;

    @Value("${sanctions.download.temp.dir:C:/temp/sanctions}")
    private String tempDirectory;

    @Value("${sanctions.opensanctions.url}")
    private String opensanctionsUrl;

    @Value("${sanctions.opensanctions.metadata.url}")
    private String metadataUrl;

    @Value("${sanctions.ingest.batch.size:500}")
    private int batchSize;

    /** Last successful pull, used by the staleness watchdog. */
    private final AtomicReference<LocalDateTime> lastSuccessfulUpdate =
            new AtomicReference<>(LocalDateTime.now().minusDays(1));

    /** Daily download. Cron preserved from the original implementation. */
    @Scheduled(cron = "${sanctions.download.cron:0 0 2 * * *}")
    @Retry(name = "sanctionsDownload")
    public void performScheduledDownload() {
        if (!downloadEnabled) {
            log.info("Sanctions download is disabled, skipping");
            return;
        }
        log.info("Starting scheduled sanctions list download...");
        try {
            downloadDailyDataset();
            lastSuccessfulUpdate.set(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error during scheduled download: {}", e.getMessage(), e);
            throw e; // trigger Retry
        }
    }

    /** Hourly watchdog — alerts if data is &gt; 26h old. */
    @Scheduled(fixedRate = 3_600_000)
    public void checkStaleData() {
        if (!downloadEnabled) return;
        long hoursSinceUpdate = ChronoUnit.HOURS.between(lastSuccessfulUpdate.get(), LocalDateTime.now());
        if (hoursSinceUpdate > 26) {
            log.error("CRITICAL: Sanctions data is STALE! Last update was {} hours ago.", hoursSinceUpdate);
        }
    }

    /**
     * Downloads OpenSanctions JSON-lines, parses each entity, and POSTs batches
     * to the aml-microservice's ingest endpoint.
     */
    public void downloadDailyDataset() {
        log.info("Downloading sanctions data from OpenSanctions...");
        try {
            String currentVersion = checkMetadataVersion();
            log.info("Latest sanctions data version: {}", currentVersion);

            Path tempDir = Paths.get(tempDirectory);
            Files.createDirectories(tempDir);

            Path downloadedFile = downloadFile(opensanctionsUrl, tempDir);
            log.info("Downloaded file: {}", downloadedFile);

            int recordsProcessed = processAndPushToMicroservice(downloadedFile);
            log.info("Successfully processed {} sanctions records", recordsProcessed);

            try {
                watchlistUpdateTrackingService.recordUpdate(
                        "OPENSANCTIONS",
                        "SANCTIONS",
                        java.time.LocalDate.now(),
                        (long) recordsProcessed,
                        opensanctionsUrl,
                        currentVersion);
                log.info("Recorded watchlist update: {} records ingested into aml-microservice", recordsProcessed);
            } catch (Exception e) {
                log.warn("Failed to record watchlist update: {}", e.getMessage());
            }

            Files.deleteIfExists(downloadedFile);
            log.info("Cleaned up temporary file");

        } catch (Exception e) {
            log.error("Failed to download and process sanctions: {}", e.getMessage(), e);
            throw new RuntimeException("Sanctions download failed", e);
        }
    }

    /** Backwards-compatible alias for callers / tests still on the old name. */
    public void downloadAndProcessSanctions() {
        downloadDailyDataset();
    }

    /** Manual trigger used by the admin endpoint. */
    public void manualDownload() {
        log.info("Manual sanctions download triggered");
        try {
            downloadDailyDataset();
            lastSuccessfulUpdate.set(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Manual download failed", e);
            throw e;
        }
    }

    // ---------------- internals ----------------

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
     * Streams the JSON-lines file, batches parsed entities, and pushes each batch
     * to the microservice. Returns the total number of entities pushed.
     */
    private int processAndPushToMicroservice(Path filePath) throws IOException {
        log.info("Processing sanctions file: {}", filePath);
        int recordCount = 0;
        int chunk = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        List<Map<String, Object>> batch = new ArrayList<>(chunk);

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    JsonNode entityJson = objectMapper.readTree(line);
                    Map<String, Object> e = parseSanctionEntity(entityJson);
                    if (e != null) {
                        batch.add(e);
                        recordCount++;
                    }
                    if (batch.size() >= chunk) {
                        pushBatch(batch);
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse entity: {}", e.getMessage());
                }
            }
            if (!batch.isEmpty()) pushBatch(batch);
        }

        log.info("Processed {} sanctions entities", recordCount);
        return recordCount;
    }

    /**
     * Map an OpenSanctions JSON-lines record onto our wire format
     * (matches {@code SanctionsIngestRequest.SanctionsEntity}).
     */
    private Map<String, Object> parseSanctionEntity(JsonNode json) {
        if (json == null) return null;

        String entityId = json.has("id") ? json.get("id").asText() : null;
        if (entityId == null || entityId.isEmpty()) return null;

        String name = json.has("caption") ? json.get("caption").asText()
                : json.has("name") ? json.get("name").asText() : "";
        String schema = json.has("schema") ? json.get("schema").asText() : "UNKNOWN";
        String type = mapSchemaToType(schema);

        List<String> aliases = new ArrayList<>();
        JsonNode props = json.get("properties");
        if (props != null) {
            collectStrings(props.get("alias"), aliases);
            collectStrings(props.get("name"), aliases);
            collectStrings(props.get("weakAlias"), aliases);
        }
        // dedupe + drop the canonical name from the alias list
        aliases.removeIf(a -> a == null || a.isBlank() || a.equalsIgnoreCase(name));

        String country = "";
        if (props != null && props.has("country")) {
            JsonNode cn = props.get("country");
            if (cn.isArray() && cn.size() > 0) country = cn.get(0).asText();
            else if (cn.isTextual()) country = cn.asText();
        }
        String birthDate = "";
        if (props != null && props.has("birthDate")) {
            JsonNode bd = props.get("birthDate");
            if (bd.isArray() && bd.size() > 0) birthDate = bd.get(0).asText();
            else if (bd.isTextual()) birthDate = bd.asText();
        }

        String listName = "OPENSANCTIONS";
        if (json.has("datasets") && json.get("datasets").isArray() && json.get("datasets").size() > 0) {
            listName = json.get("datasets").get(0).asText();
        }

        Map<String, Object> entity = new HashMap<>();
        entity.put("entityId", entityId);
        entity.put("name", name);
        entity.put("aliases", aliases);
        entity.put("type", type);
        entity.put("listName", listName);
        entity.put("country", country);
        entity.put("birthDate", birthDate);
        return entity;
    }

    private static void collectStrings(JsonNode node, List<String> out) {
        if (node == null) return;
        if (node.isArray()) {
            for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                JsonNode el = it.next();
                if (el.isTextual()) out.add(el.asText());
            }
        } else if (node.isTextual()) {
            out.add(node.asText());
        }
    }

    private static String mapSchemaToType(String schema) {
        if (schema == null) return "UNKNOWN";
        String s = schema.toLowerCase();
        if (s.contains("person")) return "PERSON";
        if (s.contains("organization") || s.contains("company") || s.contains("legalentity")) return "ORGANIZATION";
        if (s.contains("vessel")) return "VESSEL";
        return "UNKNOWN";
    }

    /**
     * POSTs a batch to {@code /internal/v1/sanctions/ingest}.
     * If the microservice is disabled or the call fails, we log and continue —
     * a partial daily ingest is better than none, and the watchdog catches total stalls.
     */
    private void pushBatch(List<Map<String, Object>> batch) {
        if (batch == null || batch.isEmpty()) return;
        if (!amlMicroserviceProperties.isEnabled()) {
            log.warn("AML microservice disabled — skipping ingest of {} sanctions entities", batch.size());
            return;
        }
        String url = amlMicroserviceProperties.getBaseUrl() + "/internal/v1/sanctions/ingest";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String authKey = amlMicroserviceProperties.getInternalAuthKey();
        if (authKey != null && !authKey.isEmpty()) {
            headers.set(INTERNAL_AUTH_HEADER, authKey);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("entities", batch);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, entity, Map.class);
            log.debug("Pushed batch of {} sanctions entities to {}", batch.size(), url);
        } catch (Exception e) {
            log.warn("Failed to push sanctions batch ({} entities) to {}: {}", batch.size(), url, e.getMessage());
        }
    }
}
