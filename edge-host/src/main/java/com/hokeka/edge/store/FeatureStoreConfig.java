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

    /**
     * How long raw transactions are retained on the client's premises, in days.
     *
     * <p><b>Single source of truth.</b> This value and the {@code default-ttl} in
     * {@code deploy/aerospike/aerospike.conf} must agree, and both are driven by
     * {@code EDGE_RETENTION_DAYS} — the installer writes it into {@code .env} and templates the
     * namespace config from the same number. They previously disagreed (90 here, 30 in the
     * namespace), which made capacity planning wrong by 3x even though the effective retention was
     * 90: every record is written with an explicit TTL, which overrides the namespace default.
     */
    @Value("${featurestore.retention-days:${EDGE_RETENTION_DAYS:90}}")
    private int retentionDays;

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
        // Log the EFFECTIVE retention. Sizing depends on it and it is set in two places that must
        // agree (see the field doc), so an operator needs to be able to confirm it from the logs
        // rather than inferring it from whichever config file they happen to open.
        log.info("On-prem retention: {} days for raw transactions (velocity buckets fixed at 26h). "
                + "Aerospike 'filesize' must be sized for this — see docs/edge-client-install-guide.md §2.",
                retentionDays);
        return new AerospikeFeatureStore(client, namespace, retentionDays);
    }
}
