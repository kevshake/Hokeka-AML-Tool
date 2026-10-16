package com.posgateway.aml.edge.tunnel;

import com.posgateway.aml.entity.edge.EdgeNode;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.io.IOException;
import java.util.List;

/**
 * Spring WebSocket adapter for {@code /api/v1/edge/tunnel}: selects the {@code hokeka-tunnel.v1}
 * subprotocol, accepts partial text messages (so the hub can enforce the 1 MiB limit with close
 * 4413 instead of the container's 1009) and delegates every event to {@link TunnelSessionHub}.
 */
public class TunnelWebSocketHandler extends AbstractWebSocketHandler implements SubProtocolCapable {

    private final TunnelSessionHub hub;

    /** Delegates to the hub. */
    public TunnelWebSocketHandler(TunnelSessionHub hub) {
        this.hub = hub;
    }

    /** §7.1: the only subprotocol; the handshake handler selects it. */
    @Override
    public List<String> getSubProtocols() {
        return List.of(TunnelFrameCodec.SUBPROTOCOL);
    }

    /** Fragments are reassembled by the hub under the size limit. */
    @Override
    public boolean supportsPartialMessages() {
        return true;
    }

    /** Registers the upgraded socket for the node authorised by the interceptor. */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Object node = session.getAttributes().get(TunnelHandshakeInterceptor.NODE_ATTRIBUTE);
        if (!(node instanceof EdgeNode edgeNode)
                || !TunnelFrameCodec.SUBPROTOCOL.equals(session.getAcceptedProtocol())) {
            session.close(new CloseStatus(TunnelSessionHub.CLOSE_FORBIDDEN, "not authorised"));
            return;
        }
        hub.onOpen(session, edgeNode);
    }

    /** One text message or fragment. */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        hub.onText(session, message.getPayload(), message.isLast());
    }

    /** §7.1: binary frames close the session with 4400. */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        hub.onBinary(session);
    }

    /** Transport failure: the socket is unusable; the close callback runs the cleanup. */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        if (session.isOpen()) {
            session.close(new CloseStatus(TunnelSessionHub.CLOSE_INTERNAL, "transport error"));
        }
    }

    /** Socket closed by either side. */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.onClosed(session, status);
    }

    /**
     * Handshake handler that never negotiates extensions: §7.1 "compression extensions are not
     * used", so {@code permessage-deflate} offers are declined.
     */
    public static final class NoExtensionsHandshakeHandler extends DefaultHandshakeHandler {

        /** Declines every requested extension. */
        @Override
        protected List<WebSocketExtension> filterRequestedExtensions(org.springframework.http.server.ServerHttpRequest request,
                                                                     List<WebSocketExtension> requested,
                                                                     List<WebSocketExtension> supported) {
            return List.of();
        }
    }
}
