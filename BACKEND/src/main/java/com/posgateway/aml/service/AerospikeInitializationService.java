package com.posgateway.aml.service;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Info;
import com.aerospike.client.policy.InfoPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Aerospike Automatic Initialization Service
 * Automatically creates indexes, sets, and ensures namespace is ready
 * Runs on application startup
 */
@Service
@ConditionalOnProperty(name = "aerospike.enabled", havingValue = "true", matchIfMissing = false)
public class AerospikeInitializationService {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeInitializationService.class);

    @Autowired
    private AerospikeConnectionService aerospikeConnectionService;

    @Value("${aerospike.namespace:test}")
    private String namespace;

    @Value("${aerospike.auto.init.enabled:true}")
    private boolean autoInitEnabled;

    @Value("${aerospike.auto.init.wait.seconds:10}")
    private int waitSeconds;

    /**
     * Automatically initialize Aerospike structures on startup
     */
    @PostConstruct
    public void initialize() {
        if (!autoInitEnabled) {
            logger.info("Aerospike auto-initialization is disabled");
            return;
        }

        if (!aerospikeConnectionService.isEnabled()) {
            logger.info("Aerospike is disabled - skipping auto-initialization");
            return;
        }

        logger.info("🚀 Starting automatic Aerospike initialization for namespace: {}", namespace);

        try {
            // Wait for connection to be ready
            waitForConnection();

            AerospikeClient client = aerospikeConnectionService.getClient();
            if (client == null) {
                logger.warn("Aerospike client is null - cannot initialize structures");
                return;
            }

            // Verify namespace exists
            verifyNamespace(client);

            // Create indexes automatically
            createIndexes(client);

            // Create initial sets (by inserting sample records if needed)
            initializeSets(client);

            logger.info("✅ Aerospike automatic initialization completed successfully");

        } catch (Exception e) {
            logger.error("❌ Error during Aerospike auto-initialization: {}", e.getMessage(), e);
            // Don't fail startup - application can still work without indexes
        }
    }

    /**
     * Wait for Aerospike connection to be ready
     */
    private void waitForConnection() {
        int attempts = 0;
        int maxAttempts = waitSeconds * 2; // Check every 500ms

        while (attempts < maxAttempts) {
            if (aerospikeConnectionService.isConnected()) {
                logger.debug("Aerospike connection verified after {} attempts", attempts);
                return;
            }
            try {
                Thread.sleep(500);
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for Aerospike connection");
                return;
            }
        }

        logger.warn("Aerospike connection not ready after {} seconds - proceeding anyway", waitSeconds);
    }

    /**
     * Verify namespace exists
     */
    private void verifyNamespace(AerospikeClient client) {
        try {
            InfoPolicy policy = new InfoPolicy();
            policy.timeout = 5000;

            String response = Info.request(policy, client.getNodes()[0], "namespaces");
            if (response != null && response.contains(namespace)) {
                logger.info("✅ Namespace '{}' verified", namespace);
            } else {
                logger.warn("⚠️ Namespace '{}' not found in Aerospike. Please create it in aerospike.conf", namespace);
                logger.info("Example namespace configuration:");
                logger.info("namespace {} {{", namespace);
                logger.info("    replication-factor 1");
                logger.info("    memory-size 1G");
                logger.info("    storage-engine memory");
                logger.info("}}");
            }
        } catch (Exception e) {
            logger.warn("Could not verify namespace '{}': {}", namespace, e.getMessage());
        }
    }

    /**
     * Create indexes automatically
     */
    private void createIndexes(AerospikeClient client) {
        logger.info("Creating Aerospike indexes...");

        try {
            // Index on entities set - name_metaphone (for phonetic matching)
            createIndexIfNotExists(client, namespace, "entities", "idx_metaphone", "name_metaphone", "STRING");

            // Index on entities set - name_metaphone_alt (alternative phonetic)
            createIndexIfNotExists(client, namespace, "entities", "idx_metaphone_alt", "name_metaphone_alt", "STRING");

            // Index on entities set - entity_type (PERSON vs ORGANIZATION)
            createIndexIfNotExists(client, namespace, "entities", "idx_entity_type", "entity_type", "STRING");

            // Index on entities set - list_name (OFAC, UN, EU, etc.)
            createIndexIfNotExists(client, namespace, "entities", "idx_list_name", "list_name", "STRING");

            // Index on pep set - name_metaphone
            createIndexIfNotExists(client, namespace, "pep", "idx_pep_metaphone", "name_metaphone", "STRING");

            // Index on pep set - name_metaphone_alt
            createIndexIfNotExists(client, namespace, "pep", "idx_pep_metaphone_alt", "name_metaphone_alt", "STRING");

            // Index on pep set - pep_level
            createIndexIfNotExists(client, namespace, "pep", "idx_pep_level", "pep_level", "STRING");

            // Index on pep set - country
            createIndexIfNotExists(client, namespace, "pep", "idx_pep_country", "country", "STRING");

            logger.info("✅ All indexes created/verified");

        } catch (Exception e) {
            logger.warn("Error creating indexes (they may already exist): {}", e.getMessage());
        }
    }

    /**
     * Create index if it doesn't exist
     */
    private void createIndexIfNotExists(AerospikeClient client, String namespace, String set, 
                                       String indexName, String binName, String indexType) {
        try {
            InfoPolicy policy = new InfoPolicy();
            policy.timeout = 5000;

            // Check if index exists
            String response = Info.request(policy, client.getNodes()[0], "sindex/" + namespace + "/" + indexName);
            if (response != null && !response.contains("FAIL")) {
                logger.debug("Index '{}' already exists on {}.{}", indexName, namespace, set);
                return;
            }

            // Create index using admin command
            String createCmd = String.format("sindex-create:ns=%s;indexname=%s;set=%s;bin=%s;indextype=%s",
                    namespace, indexName, set, binName, indexType);

            response = Info.request(policy, client.getNodes()[0], createCmd);
            if (response != null && response.contains("OK")) {
                logger.info("✅ Created index '{}' on {}.{}", indexName, namespace, set);
            } else {
                logger.debug("Index '{}' creation response: {}", indexName, response);
            }

        } catch (Exception e) {
            logger.debug("Index '{}' may already exist or creation failed: {}", indexName, e.getMessage());
        }
    }

    /**
     * Initialize sets by creating sample records if sets don't exist
     * Sets are created automatically when first record is inserted, but we can verify
     */
    private void initializeSets(AerospikeClient client) {
        logger.info("Verifying sets are ready...");

        // Sets will be created automatically when first record is inserted
        // We just verify the connection is ready for writes
        try {
            // Test write to verify namespace is writable
            com.aerospike.client.Key testKey = new com.aerospike.client.Key(namespace, "entities", "init_test");
            com.aerospike.client.Bin testBin = new com.aerospike.client.Bin("init_check", System.currentTimeMillis());
            
            com.aerospike.client.policy.WritePolicy writePolicy = new com.aerospike.client.policy.WritePolicy();
            writePolicy.expiration = 1; // Expire immediately (1 second)
            
            client.put(writePolicy, testKey, testBin);
            logger.info("✅ Namespace '{}' is writable - sets will be created automatically on first insert", namespace);

        } catch (com.aerospike.client.AerospikeException e) {
            // Handle Aerospike-specific errors gracefully
            if (e.getResultCode() == com.aerospike.client.ResultCode.INVALID_NAMESPACE) {
                logger.warn("⚠️ Namespace '{}' does not exist or is not configured. Please create it in aerospike.conf", namespace);
            } else if (e.getResultCode() == com.aerospike.client.ResultCode.NOT_AUTHENTICATED) {
                logger.warn("⚠️ Aerospike authentication required. Check security configuration.");
            } else {
                logger.warn("Could not verify namespace writability (Error {}): {}. " +
                    "This is non-critical - the application will continue, but write operations may fail. " +
                    "Ensure Aerospike server is running and namespace '{}' has write permissions.",
                    e.getResultCode(), e.getMessage(), namespace);
            }
        } catch (Exception e) {
            logger.warn("Could not verify namespace writability: {}. " +
                "This is non-critical - the application will continue, but write operations may fail.",
                e.getMessage());
        }
    }
}

