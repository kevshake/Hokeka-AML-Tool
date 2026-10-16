package com.posgateway.aml.service.edge;

import com.posgateway.aml.entity.edge.EdgeNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Authorises a machine-to-machine Edge request exactly as {@code /edge/bundle} does: the verified
 * mTLS client identity ({@link EdgeIdentityResolver}, never a bare header) must name an ACTIVE node
 * with pinned keys. Every failure mode returns empty so callers answer a bare {@code 403}.
 */
@Component
public class EdgeNodeAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(EdgeNodeAuthorizer.class);

    private final EdgeIdentityResolver identityResolver;
    private final EdgeEnrollmentService enrollmentService;

    public EdgeNodeAuthorizer(EdgeIdentityResolver identityResolver, EdgeEnrollmentService enrollmentService) {
        this.identityResolver = identityResolver;
        this.enrollmentService = enrollmentService;
    }

    /** The authorised node behind {@code request}, or empty. */
    public Optional<EdgeNode> authorize(HttpServletRequest request) {
        Optional<EdgeIdentityResolver.EdgeIdentity> identity;
        try {
            identity = identityResolver.resolve(request);
        } catch (EdgeIdentityResolver.EdgeIdentityConflictException e) {
            log.warn("Edge identity conflict from {}: {}", request.getRemoteAddr(), e.getMessage());
            return Optional.empty();
        }
        if (identity.isEmpty()) {
            log.debug("No verified edge client-certificate identity on {} {}",
                    request.getMethod(), request.getRequestURI());
            return Optional.empty();
        }
        Optional<EdgeNode> node = enrollmentService.findAuthorized(identity.get().edgeId());
        if (node.isEmpty()) {
            log.warn("Refusing edge {} (identified via {}): not an ACTIVE authorised node",
                    identity.get().edgeId(), identity.get().source());
        }
        return node;
    }

    /** Records that the node was seen (last-seen time and agent version). */
    public void recordSeen(EdgeNode node, HttpServletRequest request) {
        enrollmentService.recordSeen(node.getId(), identityResolver.agentVersion(request));
    }
}
