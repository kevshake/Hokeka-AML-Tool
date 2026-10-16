package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Extension point for cloud code that reacts to tunnel session events on the session-holding
 * instance. R9 (legal holds) implements it: §7.10 requires the cloud to re-send every
 * unacknowledged hold notify after each {@code hello.ack} ({@link #onSessionOpened}) and to record
 * {@code hold.ack} responses ({@link #onHoldAck}). Every registered bean is called; zero beans is
 * a valid configuration (no hold workflow deployed yet).
 */
public interface TunnelSessionListener {

    /** Called right after {@code hello.ack} was sent and the session registered. */
    void onSessionOpened(TunnelSessionInfo session);

    /**
     * Called for a validated {@code hold.ack} response to a notify sent by {@link TunnelNotifier}.
     *
     * @param notifyId the notify frame id the ack answers ({@code corrId})
     * @param item     the {@code notify/hold.ack.item} body item
     */
    void onHoldAck(TunnelSessionInfo session, String notifyId, ObjectNode item);

    /** Identity of a live session (ids only). */
    record TunnelSessionInfo(long pspId, String siteId, String edgeId, String sessionId, String cpInstance) {}
}
