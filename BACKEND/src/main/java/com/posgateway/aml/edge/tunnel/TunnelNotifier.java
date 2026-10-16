package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sends §7.10 {@code notify} frames ({@code config.updated}, {@code bundle.updated},
 * {@code hold.place}, {@code hold.release}) to every live session of a PSP, or to one session.
 *
 * <p>Each notify is sealed here to the addressed node and handed to the session-holding instance,
 * which delivers it and routes a {@code hold.ack} answer to {@link TunnelSessionListener#onHoldAck}.
 * Holds must reach every site and be re-sent after each {@code hello.ack}; R9 drives that through
 * {@link TunnelSessionListener#onSessionOpened} and {@link #notifySession}.
 */
public class TunnelNotifier {

    private static final Logger log = LoggerFactory.getLogger(TunnelNotifier.class);

    private final TunnelFrameCodec codec;
    private final TunnelRegistry registry;
    private final TunnelNodeDirectory directory;
    private final TunnelMessaging messaging;
    private final EdgeControlPlaneKeys cpKeys;
    private final Clock clock;

    /** One delivered notify: the session it went to and the notify frame id (= hold.ack corrId). */
    public record Delivery(String sessionId, String edgeId, String siteId, String notifyId) {}

    /** Creates the notifier. */
    public TunnelNotifier(TunnelFrameCodec codec, TunnelRegistry registry, TunnelNodeDirectory directory,
                          TunnelMessaging messaging, EdgeControlPlaneKeys cpKeys, Clock clock) {
        this.codec = codec;
        this.registry = registry;
        this.directory = directory;
        this.messaging = messaging;
        this.cpKeys = cpKeys;
        this.clock = clock;
    }

    /**
     * Sends {@code body} (validated against {@code tunnel/notify.schema.json}) to every live
     * session of the PSP.
     *
     * @return one delivery per session the notify was handed to
     * @throws IllegalArgumentException when the body fails the notify schema
     */
    public List<Delivery> notifyPsp(long pspId, ObjectNode body) {
        requireValid(body);
        List<Delivery> out = new ArrayList<>();
        for (TunnelRegistry.Entry e : registry.liveEntries(pspId, clock.instant())) {
            send(e, body).ifPresent(out::add);
        }
        return out;
    }

    /** Sends {@code body} to one live session (e.g. re-sending holds after its hello.ack). */
    public Optional<Delivery> notifySession(long pspId, String sessionId, ObjectNode body) {
        requireValid(body);
        return registry.liveEntries(pspId, clock.instant()).stream()
                .filter(e -> e.sessionId().equals(sessionId)).findFirst().flatMap(e -> send(e, body));
    }

    /** Rejects a notify body that fails its schema (the Edge would refuse it). */
    private void requireValid(ObjectNode body) {
        List<String> bad = codec.checkBody("notify", body);
        if (!bad.isEmpty()) {
            throw new IllegalArgumentException("notify body fails tunnel/notify.schema.json at " + bad);
        }
    }

    /** Seals the notify to the session's node and hands it to the session holder. */
    private Optional<Delivery> send(TunnelRegistry.Entry e, ObjectNode body) {
        Optional<TunnelNodeDirectory.NodeKeys> keys = directory.keys(e.edgeId());
        if (keys.isEmpty()) {
            return Optional.empty();
        }
        String id = TunnelFrameCodec.newId();
        try {
            String frame = codec.seal(new TunnelFrameCodec.FrameSpec("notify", id, null, e.sessionId(), e.edgeId(),
                    body), keys.get().x25519Raw(), cpKeys.signingPrivateKey(), clock.instant());
            messaging.toHolder(e.cpInstance(), RelayMessage.withFrame(RelayMessage.Kind.NOTIFY, e.sessionId(), id,
                    messaging.instanceId(), frame));
            return Optional.of(new Delivery(e.sessionId(), e.edgeId(), e.siteId(), id));
        } catch (TunnelFrameException ex) {
            log.warn("Tunnel notify to session {} could not be sealed ({})", e.sessionId(), ex.code());
            return Optional.empty();
        }
    }
}
