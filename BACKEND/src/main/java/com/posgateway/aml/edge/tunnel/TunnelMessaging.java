package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Cross-instance transport of {@link RelayMessage}s over Redis pub/sub (§7.12):
 * holder-bound messages on {@code tunnel:cp:{cpInstance}}, origin-bound ones on
 * {@code tunnel:reply:{originInstance}}, and {@code tunnel:broadcast} for every instance.
 *
 * <p>A message addressed to this instance is handed to the local handler directly, without a
 * Redis round trip. Pub/sub is not persisted and nothing else is written to Redis here. The
 * handler wiring is set once at startup ({@link #bind}) to break the hub/client cycle.
 */
public class TunnelMessaging implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(TunnelMessaging.class);

    static final String HOLDER_CHANNEL_PREFIX = "tunnel:cp:";
    static final String REPLY_CHANNEL_PREFIX = "tunnel:reply:";
    static final String BROADCAST_CHANNEL = "tunnel:broadcast";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String instanceId;
    private volatile Consumer<RelayMessage> holderHandler;
    private volatile Consumer<RelayMessage> originHandler;
    private volatile Consumer<RelayMessage> broadcastHandler;

    /** Creates the transport for this instance. */
    public TunnelMessaging(StringRedisTemplate redis, ObjectMapper mapper, String instanceId) {
        this.redis = redis;
        this.mapper = mapper;
        this.instanceId = instanceId;
    }

    /** Name of this Control Plane instance. */
    public String instanceId() {
        return instanceId;
    }

    /** Channel this instance receives holder-bound messages on. */
    public String holderChannel() {
        return HOLDER_CHANNEL_PREFIX + instanceId;
    }

    /** Channel this instance receives origin-bound replies on. */
    public String replyChannel() {
        return REPLY_CHANNEL_PREFIX + instanceId;
    }

    /** Installs the local handlers (session hub, request client, broadcast consumer). */
    public void bind(Consumer<RelayMessage> holder, Consumer<RelayMessage> origin, Consumer<RelayMessage> broadcast) {
        this.holderHandler = holder;
        this.originHandler = origin;
        this.broadcastHandler = broadcast;
    }

    /** Sends a holder-bound message to the instance holding the session. */
    public void toHolder(String cpInstance, RelayMessage message) {
        if (instanceId.equals(cpInstance)) {
            holderHandler.accept(message);
        } else {
            publish(HOLDER_CHANNEL_PREFIX + cpInstance, message);
        }
    }

    /** Sends an origin-bound message to the instance that started the request. */
    public void toOrigin(String origin, RelayMessage message) {
        if (instanceId.equals(origin)) {
            originHandler.accept(message);
        } else {
            publish(REPLY_CHANNEL_PREFIX + origin, message);
        }
    }

    /** Publishes to every instance, including this one (delivered by the subscription). */
    public void broadcast(RelayMessage message) {
        publish(BROADCAST_CHANNEL, message);
    }

    /** Serialises and publishes; a serialisation failure is a programming error. */
    private void publish(String channel, RelayMessage message) {
        try {
            redis.convertAndSend(channel, mapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("relay message not serialisable", e);
        }
    }

    /** Redis subscription callback: dispatch by channel. Malformed messages are dropped. */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        RelayMessage relay;
        try {
            relay = mapper.readValue(message.getBody(), RelayMessage.class);
        } catch (Exception e) {
            log.warn("Tunnel relay: dropping malformed message on {}", channel);
            return;
        }
        try {
            if (channel.equals(holderChannel())) {
                holderHandler.accept(relay);
            } else if (channel.equals(replyChannel())) {
                originHandler.accept(relay);
            } else if (channel.equals(BROADCAST_CHANNEL)) {
                broadcastHandler.accept(relay);
            }
        } catch (RuntimeException e) {
            log.warn("Tunnel relay: handler failed for {} message ({})", relay.kind(), e.getClass().getSimpleName());
        }
    }
}
