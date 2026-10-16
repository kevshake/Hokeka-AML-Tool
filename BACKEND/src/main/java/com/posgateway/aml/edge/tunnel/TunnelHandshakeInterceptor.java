package com.posgateway.aml.edge.tunnel;

import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.service.edge.EdgeIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Upgrade-time checks of §7.1, run <b>before</b> the 101 is sent:
 * <ol>
 *   <li>identity from the verified client certificate (JVM mTLS) or the trusted-proxy
 *       {@code X-SSL-Client-*} headers, via the existing {@link EdgeIdentityResolver} — a bare
 *       {@code X-Hokeka-Edge-Id} never authenticates and a conflicting one is refused;</li>
 *   <li>the node is authorised (ACTIVE) exactly as for {@code /edge/bundle};</li>
 *   <li>the client offers the {@code hokeka-tunnel.v1} subprotocol.</li>
 * </ol>
 * A failure of 1–2 answers {@code 403} with no body detail; a missing subprotocol {@code 400}.
 */
public class TunnelHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TunnelHandshakeInterceptor.class);

    /** Handshake attribute carrying the authorised node to the handler. */
    static final String NODE_ATTRIBUTE = "hokeka.tunnel.node";

    private final EdgeIdentityResolver identityResolver;
    private final TunnelNodeAuthorizer authorizer;

    /** Creates the interceptor over the existing identity resolver and node gate. */
    public TunnelHandshakeInterceptor(EdgeIdentityResolver identityResolver, TunnelNodeAuthorizer authorizer) {
        this.identityResolver = identityResolver;
        this.authorizer = authorizer;
    }

    /** Authenticates and authorises the upgrade; refuses with 403/400 otherwise. */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servlet)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        HttpServletRequest http = servlet.getServletRequest();
        Optional<EdgeIdentityResolver.EdgeIdentity> identity;
        try {
            identity = identityResolver.resolve(http);
        } catch (EdgeIdentityResolver.EdgeIdentityConflictException e) {
            log.warn("Tunnel upgrade refused: edge identity conflict from {}", http.getRemoteAddr());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        if (identity.isEmpty()) {
            log.debug("Tunnel upgrade refused: no verified client certificate identity");
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        Optional<EdgeNode> node = authorizer.findAuthorized(identity.get().edgeId());
        if (node.isEmpty()) {
            log.warn("Tunnel upgrade refused for edge {}: not an ACTIVE authorised node", identity.get().edgeId());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        if (!offersSubprotocol(request.getHeaders().get("Sec-WebSocket-Protocol"))) {
            log.warn("Tunnel upgrade refused for edge {}: subprotocol {} not offered", identity.get().edgeId(),
                    TunnelFrameCodec.SUBPROTOCOL);
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        attributes.put(NODE_ATTRIBUTE, node.get());
        return true;
    }

    /** Nothing to do after the handshake. */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
        // The handler takes over in afterConnectionEstablished.
    }

    /** True when one of the (comma-separated, possibly repeated) offered subprotocols is ours. */
    static boolean offersSubprotocol(List<String> headerValues) {
        if (headerValues == null) {
            return false;
        }
        for (String v : headerValues) {
            for (String p : v.split(",")) {
                if (TunnelFrameCodec.SUBPROTOCOL.equals(p.trim())) {
                    return true;
                }
            }
        }
        return false;
    }
}
