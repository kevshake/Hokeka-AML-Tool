package com.posgateway.aml.edge.tunnel;

import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;

import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Control Plane knowledge about Edge Nodes and Sites that the tunnel needs: which nodes are
 * authorised (exactly the {@code /edge/bundle} gate, §7.1), which sites a PSP has, each node's
 * pinned keys, and a site's last-seen instant for {@code EDGE_OFFLINE.lastSeenAt}.
 */
public class TunnelNodeDirectory {

    private final TunnelNodeAuthorizer authorizer;
    private final EdgeNodeRepository nodes;
    private final TunnelRegistry registry;

    /** Pinned public keys of one node, decoded. */
    public record NodeKeys(byte[] x25519Raw, PublicKey ed25519) {}

    /**
     * @param authorizer the {@code /edge/bundle} authorisation ({@code EdgeEnrollmentService
     *                   .findAuthorized}): node ACTIVE, keys pinned, owning PSP ACTIVE
     */
    public TunnelNodeDirectory(TunnelNodeAuthorizer authorizer, EdgeNodeRepository nodes,
                               TunnelRegistry registry) {
        this.authorizer = authorizer;
        this.nodes = nodes;
        this.registry = registry;
    }

    /** The node when it is currently authorised to use the edge channel. */
    public Optional<EdgeNode> authorized(String edgeId) {
        return authorizer.findAuthorized(edgeId);
    }

    /** Decodes a node's pinned keys (base64 raw 32-byte X25519 / Ed25519). */
    public static NodeKeys keysOf(EdgeNode node) {
        byte[] x = Base64.getDecoder().decode(node.getEdgeX25519PublicKey());
        byte[] ed = Base64.getDecoder().decode(node.getEdgeEd25519PublicKey());
        return new NodeKeys(x, EdgeControlPlaneKeys.ed25519PublicFromRaw(ed));
    }

    /** Keys of an authorised node, or empty when it is no longer authorised. */
    public Optional<NodeKeys> keys(String edgeId) {
        return authorized(edgeId).map(TunnelNodeDirectory::keysOf);
    }

    /** ACTIVE nodes of a PSP grouped by site id, sites in first-seen order. */
    public Map<String, List<EdgeNode>> sites(long pspId) {
        Map<String, List<EdgeNode>> out = new LinkedHashMap<>();
        for (EdgeNode n : nodes.findByPspIdAndStatus(pspId, EdgeNodeStatus.ACTIVE)) {
            out.computeIfAbsent(n.effectiveSiteId(), k -> new ArrayList<>()).add(n);
        }
        return out;
    }

    /**
     * Last time anything was heard from a site: the latest tunnel frame ({@code edge:lastseen}),
     * else the latest HTTPS poll ({@code edge_nodes.last_seen_at}), else activation. {@code EPOCH}
     * means "never" — the schema requires a value.
     */
    public Instant lastSeen(List<EdgeNode> siteNodes) {
        Instant best = Instant.EPOCH;
        for (EdgeNode n : siteNodes) {
            best = later(best, registry.lastSeen(n.getEdgeId()).orElse(null));
            best = later(best, n.getLastSeenAt());
            best = later(best, n.getActivatedAt());
        }
        return best;
    }

    /** The later of two instants, ignoring {@code null}. */
    private static Instant later(Instant a, Instant b) {
        return b != null && b.isAfter(a) ? b : a;
    }
}
