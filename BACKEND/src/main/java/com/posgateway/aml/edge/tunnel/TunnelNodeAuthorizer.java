package com.posgateway.aml.edge.tunnel;

import com.posgateway.aml.entity.edge.EdgeNode;

import java.util.Optional;

/**
 * The edge-channel authorisation the tunnel reuses (§7.1: "require the node ACTIVE before
 * completing the upgrade", same gate as {@code /edge/bundle}). In production this is
 * {@code EdgeEnrollmentService.findAuthorized}: node ACTIVE, keys pinned, owning PSP ACTIVE.
 */
@FunctionalInterface
public interface TunnelNodeAuthorizer {

    /** The node when {@code edgeId} is currently authorised, else empty. */
    Optional<EdgeNode> findAuthorized(String edgeId);
}
