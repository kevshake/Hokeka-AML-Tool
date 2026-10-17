package com.posgateway.aml.service.edgedata;

import com.posgateway.aml.edge.tunnel.EdgeDataAccessAuditor;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.EdgeDataGateway;
import com.posgateway.aml.edge.tunnel.JdbcSupportAccessPolicy;
import com.posgateway.aml.edge.tunnel.SupportAccessPolicy;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelNodeDirectory;
import com.posgateway.aml.edge.tunnel.TunnelRegistry;
import com.posgateway.aml.repository.edge.EdgeDataAccessLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The R4 tunnel beans as the R6 proxy uses them. They exist only with {@code edge.tunnel.enabled=true}
 * ({@code EdgeTunnelConfiguration}); without them every Edge pull answers {@code 503 EDGE_OFFLINE}
 * and the missing setting is logged once, by name. The access auditor and the support-access policy
 * fall back to the same implementations over the application database, so legacy reads stay gated
 * and audited either way.
 */
@Component
public class EdgeTunnelAccess {

    private static final Logger log = LoggerFactory.getLogger(EdgeTunnelAccess.class);

    private final ObjectProvider<EdgeDataGateway> gateway;
    private final ObjectProvider<TunnelNodeDirectory> directory;
    private final ObjectProvider<TunnelRegistry> registry;
    private final ObjectProvider<EdgeDataAccessAuditor> auditor;
    private final ObjectProvider<SupportAccessPolicy> supportAccess;
    private final EdgeDataAccessAuditor fallbackAuditor;
    private final SupportAccessPolicy fallbackSupportAccess;
    private final AtomicBoolean disabledLogged = new AtomicBoolean();

    /** Resolves the tunnel beans lazily; the fallbacks use the application's database. */
    public EdgeTunnelAccess(ObjectProvider<EdgeDataGateway> gateway, ObjectProvider<TunnelNodeDirectory> directory,
                            ObjectProvider<TunnelRegistry> registry, ObjectProvider<EdgeDataAccessAuditor> auditor,
                            ObjectProvider<SupportAccessPolicy> supportAccess, EdgeDataAccessLogRepository auditRepository,
                            JdbcTemplate jdbc) {
        this.gateway = gateway;
        this.directory = directory;
        this.registry = registry;
        this.auditor = auditor;
        this.supportAccess = supportAccess;
        this.fallbackAuditor = new EdgeDataAccessAuditor(auditRepository, Clock.systemUTC());
        this.fallbackSupportAccess = new JdbcSupportAccessPolicy(jdbc);
    }

    /** True when this Control Plane runs the tunnel server. */
    public boolean tunnelEnabled() {
        return gateway.getIfAvailable() != null;
    }

    /**
     * The gateway.
     *
     * @throws EdgeDataException {@code 503 EDGE_OFFLINE} when the tunnel is disabled here
     */
    public EdgeDataGateway gateway() {
        EdgeDataGateway g = gateway.getIfAvailable();
        if (g == null) {
            if (disabledLogged.compareAndSet(false, true)) {
                log.warn("Edge data pulls unavailable: edge.tunnel.enabled is false on this Control Plane");
            }
            throw new EdgeDataException(TunnelError.offline(null, Instant.EPOCH));
        }
        return g;
    }

    /** The node/site directory, when the tunnel runs. */
    public Optional<TunnelNodeDirectory> directory() {
        return Optional.ofNullable(directory.getIfAvailable());
    }

    /** The session registry, when the tunnel runs. */
    public Optional<TunnelRegistry> registry() {
        return Optional.ofNullable(registry.getIfAvailable());
    }

    /** The §7.15 access auditor (the tunnel's bean, else one over the same table). */
    public EdgeDataAccessAuditor auditor() {
        EdgeDataAccessAuditor a = auditor.getIfAvailable();
        return a != null ? a : fallbackAuditor;
    }

    /** The §9 support-access policy (the tunnel's bean, else the same JDBC policy). */
    public SupportAccessPolicy supportAccess() {
        SupportAccessPolicy p = supportAccess.getIfAvailable();
        return p != null ? p : fallbackSupportAccess;
    }
}
