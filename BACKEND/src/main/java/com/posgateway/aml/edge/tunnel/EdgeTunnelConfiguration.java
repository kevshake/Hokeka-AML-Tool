package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.repository.edge.EdgeDataAccessLogRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.service.edge.EdgeIdentityResolver;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Wires the Edge Tunnel server (work package R4; {@code docs/EDGE_DATA_RESIDENCY_CONTRACT.md} §7):
 * the WebSocket endpoint {@code /edge/tunnel} (served as {@code /api/v1/edge/tunnel} under the
 * servlet context path), the Redis registry and pub/sub relay, the session hub, the request
 * client, the {@link EdgeDataGateway} API, the notifier and the access auditor.
 *
 * <p>It needs four policy beans, supplied in production by {@link EdgeTunnelDefaultsConfiguration}:
 * {@link TunnelNodeAuthorizer}, {@link SupportAccessPolicy}, {@link TunnelPspSettingsProvider} and
 * {@link TunnelBundleVersionSource}. Disabled entirely with {@code edge.tunnel.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "edge.tunnel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableWebSocket
@EnableConfigurationProperties(TunnelProperties.class)
public class EdgeTunnelConfiguration {

    /** Mapper private to the tunnel: no application-wide customisation can change the wire format. */
    private static final ObjectMapper TUNNEL_MAPPER = new ObjectMapper();

    /** Contract schema validator over {@code classpath:contracts/}. */
    @Bean
    public TunnelSchemas tunnelSchemas() {
        return new TunnelSchemas();
    }

    /** Wall clock of the tunnel (UTC). */
    @Bean
    public Clock tunnelClock() {
        return Clock.systemUTC();
    }

    /** Timers: deadlines and the housekeeping tick (wrapped, see {@link TunnelTimers}). */
    @Bean(destroyMethod = "close")
    public TunnelTimers tunnelTimers() {
        return new TunnelTimers();
    }

    /**
     * String template for the registry and relay (ids and sealed frames only). Not a bean: a
     * {@code StringRedisTemplate} bean would make Spring Boot skip its own
     * {@code stringRedisTemplate}.
     */
    private static StringRedisTemplate tunnelRedis(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /** Frame codec over the existing HSE-1 envelope and replay guard. */
    @Bean
    public TunnelFrameCodec tunnelFrameCodec(HokekaSecureEnvelope hse, EdgeReplayGuard replayGuard,
                                             TunnelSchemas schemas, TunnelProperties props) {
        return new TunnelFrameCodec(hse, replayGuard, schemas, TUNNEL_MAPPER, props.getMaxFrameBytes());
    }

    /** Redis session registry. */
    @Bean
    public TunnelRegistry tunnelRegistry(RedisConnectionFactory connectionFactory, TunnelProperties props) {
        return new TunnelRegistry(tunnelRedis(connectionFactory), TUNNEL_MAPPER, Duration.ofMillis(props.getRegistryStaleMs()));
    }

    /** Cross-instance pub/sub transport, named after this instance. */
    @Bean
    public TunnelMessaging tunnelMessaging(RedisConnectionFactory connectionFactory, TunnelProperties props) {
        return new TunnelMessaging(tunnelRedis(connectionFactory), TUNNEL_MAPPER, resolveInstanceId(props.getInstanceId()));
    }

    /** Subscribes this instance to its holder, reply and broadcast channels. */
    @Bean
    public RedisMessageListenerContainer tunnelListenerContainer(RedisConnectionFactory connectionFactory,
                                                                 TunnelMessaging messaging) {
        RedisMessageListenerContainer c = new RedisMessageListenerContainer();
        c.setConnectionFactory(connectionFactory);
        c.addMessageListener(messaging, List.of(new ChannelTopic(messaging.holderChannel()),
                new ChannelTopic(messaging.replyChannel()), new ChannelTopic(TunnelMessaging.BROADCAST_CHANNEL)));
        return c;
    }

    /** Node/site directory. */
    @Bean
    public TunnelNodeDirectory tunnelNodeDirectory(TunnelNodeAuthorizer authorizer, EdgeNodeRepository nodes,
                                                   TunnelRegistry registry) {
        return new TunnelNodeDirectory(authorizer, nodes, registry);
    }

    /** Session-holder side. */
    @Bean
    public TunnelSessionHub tunnelSessionHub(TunnelProperties props, TunnelFrameCodec codec,
                                             TunnelNodeDirectory directory, TunnelRegistry registry,
                                             TunnelMessaging messaging, TunnelPspSettingsProvider settings,
                                             EdgeControlPlaneKeys cpKeys, TunnelBundleVersionSource bundleVersions,
                                             ObjectProvider<TunnelSessionListener> listeners, Clock tunnelClock) {
        return new TunnelSessionHub(props, codec, directory, registry, messaging, settings, cpKeys,
                bundleVersions::currentBundleVersion, listeners.orderedStream().toList(), tunnelClock, TUNNEL_MAPPER);
    }

    /** Origin side. */
    @Bean
    public EdgeTunnelClient edgeTunnelClient(TunnelProperties props, TunnelFrameCodec codec, TunnelRegistry registry,
                                             TunnelNodeDirectory directory, TunnelMessaging messaging,
                                             EdgeControlPlaneKeys cpKeys, Clock tunnelClock,
                                             TunnelTimers tunnelTimers) {
        return new EdgeTunnelClient(props, codec, registry, directory, messaging, cpKeys, tunnelClock,
                tunnelTimers.executor(), TUNNEL_MAPPER);
    }

    /** Cloud access audit writer. */
    @Bean
    public EdgeDataAccessAuditor edgeDataAccessAuditor(EdgeDataAccessLogRepository repository, Clock tunnelClock) {
        return new EdgeDataAccessAuditor(repository, tunnelClock);
    }

    /**
     * Composite cursor signer. The secret is {@code edge.tunnel.cursor-secret} or, when blank,
     * derived from the Control Plane signing key, which every instance shares, so cursors verify
     * on any instance.
     */
    @Bean
    public CompositeCursorCodec compositeCursorCodec(TunnelProperties props, EdgeControlPlaneKeys cpKeys,
                                                     TunnelSchemas schemas) throws Exception {
        byte[] secret;
        if (props.getCursorSecret() != null && !props.getCursorSecret().isBlank()) {
            secret = props.getCursorSecret().getBytes(StandardCharsets.UTF_8);
        } else {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update("hokeka.tunnel.cursor.v1".getBytes(StandardCharsets.US_ASCII));
            d.update(cpKeys.signingPrivateKey().getEncoded());
            secret = d.digest();
        }
        return new CompositeCursorCodec(secret, TUNNEL_MAPPER, schemas);
    }

    /** The Java API for cloud code. */
    @Bean
    public EdgeDataGateway edgeDataGateway(EdgeTunnelClient client, TunnelNodeDirectory directory,
                                           SupportAccessPolicy supportAccess, EdgeDataAccessAuditor auditor,
                                           CompositeCursorCodec cursors, TunnelSchemas schemas,
                                           TunnelMessaging messaging, TunnelProperties props) {
        return new EdgeDataGateway(client, directory, supportAccess, auditor, cursors, schemas, messaging, props,
                TUNNEL_MAPPER);
    }

    /** Notify sender. */
    @Bean
    public TunnelNotifier tunnelNotifier(TunnelFrameCodec codec, TunnelRegistry registry, TunnelNodeDirectory directory,
                                         TunnelMessaging messaging, EdgeControlPlaneKeys cpKeys, Clock tunnelClock) {
        return new TunnelNotifier(codec, registry, directory, messaging, cpKeys, tunnelClock);
    }

    /**
     * Binds the relay handlers (before any listener container starts) and runs the housekeeping
     * tick; on shutdown closes every local session with 4000 so Edges reconnect elsewhere.
     */
    @Bean
    public SmartLifecycle tunnelLifecycle(TunnelMessaging messaging, TunnelSessionHub hub, EdgeTunnelClient client,
                                          EdgeDataGateway gateway, TunnelTimers tunnelTimers,
                                          TunnelProperties props) {
        messaging.bind(hub::onHolderMessage, client::onOriginMessage, gateway::onBroadcast);
        return new SmartLifecycle() {
            private volatile ScheduledFuture<?> tick;

            /** Starts the housekeeping tick. */
            @Override
            public void start() {
                tick = tunnelTimers.executor().scheduleWithFixedDelay(() -> {
                    try {
                        hub.housekeeping();
                    } catch (RuntimeException e) {
                        org.slf4j.LoggerFactory.getLogger(EdgeTunnelConfiguration.class)
                                .warn("Tunnel housekeeping failed: {}", e.getClass().getSimpleName());
                    }
                }, props.getHousekeepingIntervalMs(), props.getHousekeepingIntervalMs(), TimeUnit.MILLISECONDS);
            }

            /** Stops the tick and closes local sessions with 4000 (instance going away). */
            @Override
            public void stop() {
                if (tick != null) {
                    tick.cancel(false);
                }
                hub.shutdown();
            }

            /** Running while the tick is scheduled. */
            @Override
            public boolean isRunning() {
                return tick != null && !tick.isCancelled();
            }

            /** Stop before the web server and Redis connections go away. */
            @Override
            public int getPhase() {
                return Integer.MAX_VALUE - 1000;
            }
        };
    }

    /** Spring WebSocket handler. */
    @Bean
    public TunnelWebSocketHandler tunnelWebSocketHandler(TunnelSessionHub hub) {
        return new TunnelWebSocketHandler(hub);
    }

    /** Registers {@code /edge/tunnel} with the handshake interceptor and the no-extensions handler. */
    @Bean
    public WebSocketConfigurer tunnelWebSocketConfigurer(TunnelWebSocketHandler handler,
                                                         EdgeIdentityResolver identityResolver,
                                                         TunnelNodeAuthorizer authorizer) {
        return (WebSocketHandlerRegistry registry) -> registry.addHandler(handler, "/edge/tunnel")
                .addInterceptors(new TunnelHandshakeInterceptor(identityResolver, authorizer))
                .setHandshakeHandler(new TunnelWebSocketHandler.NoExtensionsHandshakeHandler());
    }

    /**
     * {@code edge.tunnel.instance-id}, or host name + random suffix, sanitised to the
     * {@code cpInstance} pattern {@code ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$}.
     */
    static String resolveInstanceId(String configured) {
        String base = configured;
        if (base == null || base.isBlank()) {
            String host;
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "cp";
            }
            base = host + "-" + HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt()).substring(0, 6);
        }
        String clean = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (clean.isEmpty() || !Character.isLetterOrDigit(clean.charAt(0))) {
            clean = "cp" + clean;
        }
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }
}
