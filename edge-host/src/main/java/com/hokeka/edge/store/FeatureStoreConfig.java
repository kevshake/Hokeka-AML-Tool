package com.hokeka.edge.store;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.policy.ClientPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the on-premises Aerospike feature store.
 *
 * <p>Reads the {@code FEATURESTORE_*} environment variables that {@code deploy/docker-compose.yml}
 * has always set (and which, until now, nothing read — Aerospike was provisioned as a container the
 * edge depended on but never talked to).
 *
 * <p>Connection failure is <b>not</b> fatal: the edge falls back to {@link NoOpFeatureStore} and keeps
 * serving decisions from caller-supplied features. Losing the store must degrade enrichment, never
 * take the node down — but it is logged loudly and surfaced on {@code /edge/status}, because a node
 * running without local history silently cannot fire velocity rules.
 */
@Configuration
public class FeatureStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(FeatureStoreConfig.class);

    @Value("${featurestore.host:${FEATURESTORE_HOST:}}")
    private String host;

    @Value("${featurestore.port:${FEATURESTORE_PORT:3000}}")
    private int port;

    @Value("${featurestore.namespace:${FEATURESTORE_NAMESPACE:hokeka}}")
    private String namespace;

    @Bean(destroyMethod = "close")
    public AerospikeClient aerospikeClient() {
        if (host == null || host.isBlank()) {
            return null; // no store configured — featureStore() falls back below
        }
        try {
            ClientPolicy policy = new ClientPolicy();
            policy.timeout = 2000;
            policy.failIfNotConnected = true;
            AerospikeClient client = new AerospikeClient(policy, new Host(host, port));
            log.info("On-prem feature store connected: {}:{} namespace '{}'", host, port, namespace);
            return client;
        } catch (Exception e) {
            log.error("Could not connect to the on-prem feature store at {}:{} — running WITHOUT local "
                    + "history. Velocity/history rules cannot fire until this is restored. Cause: {}",
                    host, port, e.getMessage());
            return null;
        }
    }

    /**
     * {@code ObjectProvider} rather than a direct parameter: a {@code @Bean} method returning null
     * registers no bean, so a plain {@code AerospikeClient} parameter would fail to autowire on a
     * node with no store configured — turning a degraded-but-working edge into a boot failure.
     */
    @Bean
    public EdgeFeatureStore featureStore(
            org.springframework.beans.factory.ObjectProvider<AerospikeClient> clientProvider) {
        AerospikeClient client = clientProvider.getIfAvailable();
        if (client == null) {
            log.warn("No on-prem feature store configured (FEATURESTORE_HOST unset or unreachable) — "
                    + "evaluating only on caller-supplied features.");
            return new NoOpFeatureStore();
        }
        // Aerospike now holds only velocity buckets (26 h TTL) and the rule bundle; transaction
        // retention lives on the Edge Ledger and is set per PSP (minimum 5 years).
        log.info("On-prem feature store: velocity buckets (26 h TTL) + rule bundle in namespace '{}'", namespace);
        return new AerospikeFeatureStore(client, namespace);
    }
}
