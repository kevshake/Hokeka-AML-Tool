package com.hokeka.edge.tunnel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal RFC 6455 WebSocket server over TLS 1.3 with mandatory client certificates, standing in
 * for the Control Plane's {@code /api/v1/edge/tunnel} endpoint in the interop test. It does the
 * upgrade (subprotocol {@code hokeka-tunnel.v1}, or a scripted refusal), reads masked client
 * frames (text, continuation, binary, close, ping) and writes unmasked server frames. Tunnel
 * semantics live in the test, which drives each {@link Session}.
 */
final class FakeTunnelServer implements AutoCloseable {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final SSLServerSocket server;
    private final Thread acceptor;
    /** Accepted, upgraded sessions in order. */
    final BlockingQueue<Session> sessions = new LinkedBlockingQueue<>();
    /** Upgrade attempts (including refused ones). */
    final AtomicInteger upgrades = new AtomicInteger();
    /** HTTP status to answer the next upgrades with instead of 101 (0 = upgrade normally). */
    volatile int refuseWith;
    /** Whether to echo the subprotocol on upgrade. */
    volatile boolean selectSubprotocol = true;

    /** Binds to an ephemeral loopback port. */
    FakeTunnelServer(SSLContext ctx) throws IOException {
        server = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(0, 50, InetAddress.getLoopbackAddress());
        server.setEnabledProtocols(new String[]{"TLSv1.3"});
        server.setNeedClientAuth(true);
        acceptor = new Thread(this::acceptLoop, "fake-tunnel-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** The bound port. */
    int port() {
        return server.getLocalPort();
    }

    /** Accepts connections until closed. */
    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                SSLSocket s = (SSLSocket) server.accept();
                Thread t = new Thread(() -> serve(s), "fake-tunnel-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    /** Upgrade, then read frames until the socket closes. */
    private void serve(SSLSocket socket) {
        try {
            socket.startHandshake();
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            Map<String, String> headers = readRequest(in);
            upgrades.incrementAndGet();
            String peer = ((X509Certificate) socket.getSession().getPeerCertificates()[0]).getSubjectX500Principal()
                    .getName();
            if (refuseWith != 0) {
                out.write(("HTTP/1.1 " + refuseWith + " Refused\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                out.flush();
                socket.close();
                return;
            }
            String key = headers.get("sec-websocket-key");
            String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
            StringBuilder resp = new StringBuilder("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n");
            String offered = headers.getOrDefault("sec-websocket-protocol", "");
            if (selectSubprotocol && offered.contains(TunnelFrameCodec.SUBPROTOCOL)) {
                resp.append("Sec-WebSocket-Protocol: ").append(TunnelFrameCodec.SUBPROTOCOL).append("\r\n");
            }
            resp.append("\r\n");
            out.write(resp.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Session session = new Session(socket, out, headers, peer);
            sessions.add(session);
            session.readLoop(new DataInputStream(in));
        } catch (Exception e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
    }

    /** Reads the HTTP upgrade request; header names lower-cased. */
    private static Map<String, String> readRequest(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("eof during upgrade");
            }
            buf.write(b);
            state = (b == '\r' && (state == 0 || state == 2)) || (b == '\n' && (state == 1 || state == 3)) ? state + 1 : 0;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        String[] lines = buf.toString(StandardCharsets.US_ASCII).split("\r\n");
        headers.put(":request", lines[0]);
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) {
                headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim());
            }
        }
        return headers;
    }

    /** Stops accepting and closes every session. */
    @Override
    public void close() throws IOException {
        server.close();
        for (Session s : sessions) {
            s.abort();
        }
    }

    /** One upgraded connection. */
    static final class Session {
        final SSLSocket socket;
        final Map<String, String> headers;
        final String peerSubject;
        /** Text messages received from the Edge, in order. */
        final BlockingQueue<String> texts = new LinkedBlockingQueue<>();
        /** The close code the Edge sent (completes when its close frame arrives or the socket drops: -1). */
        final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
        private final OutputStream out;

        Session(SSLSocket socket, OutputStream out, Map<String, String> headers, String peerSubject) {
            this.socket = socket;
            this.out = out;
            this.headers = headers;
            this.peerSubject = peerSubject;
        }

        /** Reads client frames: assembles text, answers close, records everything. */
        void readLoop(DataInputStream in) {
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            try {
                while (true) {
                    int b0 = in.readUnsignedByte();
                    int b1 = in.readUnsignedByte();
                    boolean fin = (b0 & 0x80) != 0;
                    int opcode = b0 & 0x0F;
                    boolean masked = (b1 & 0x80) != 0;
                    long len = b1 & 0x7F;
                    if (len == 126) {
                        len = in.readUnsignedShort();
                    } else if (len == 127) {
                        len = in.readLong();
                    }
                    byte[] mask = new byte[4];
                    if (masked) {
                        in.readFully(mask);
                    }
                    byte[] payload = new byte[(int) len];
                    in.readFully(payload);
                    if (masked) {
                        for (int i = 0; i < payload.length; i++) {
                            payload[i] ^= mask[i % 4];
                        }
                    }
                    switch (opcode) {
                        case 0x1, 0x0 -> {
                            message.write(payload);
                            if (fin) {
                                texts.add(message.toString(StandardCharsets.UTF_8));
                                message.reset();
                            }
                        }
                        case 0x8 -> {
                            int code = payload.length >= 2 ? ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF) : 1005;
                            closeCode.complete(code);
                            writeFrame(0x8, payload.length >= 2 ? new byte[]{payload[0], payload[1]} : new byte[0]);
                            socket.close();
                            return;
                        }
                        case 0x9 -> writeFrame(0xA, payload);
                        default -> { /* binary from the Edge is never sent; pong ignored */ }
                    }
                }
            } catch (IOException e) {
                closeCode.complete(-1);
            }
        }

        /** Sends one text message. */
        void sendText(String text) throws IOException {
            writeFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
        }

        /** Sends one binary message. */
        void sendBinary(byte[] data) throws IOException {
            writeFrame(0x2, data);
        }

        /** Sends a close frame with {@code code} and closes the socket shortly after. */
        void sendClose(int code, String reason) throws IOException {
            byte[] r = reason.getBytes(StandardCharsets.UTF_8);
            byte[] p = new byte[2 + r.length];
            p[0] = (byte) (code >> 8);
            p[1] = (byte) code;
            System.arraycopy(r, 0, p, 2, r.length);
            writeFrame(0x8, p);
        }

        /** Drops the TCP connection without a close frame. */
        void abort() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
        }

        /** Next text message within {@code seconds}, or null. */
        String next(long seconds) throws InterruptedException {
            return texts.poll(seconds, TimeUnit.SECONDS);
        }

        /** Writes one unmasked server frame. */
        synchronized void writeFrame(int opcode, byte[] payload) throws IOException {
            try {
                ByteArrayOutputStream f = new ByteArrayOutputStream();
                f.write(0x80 | opcode);
                if (payload.length < 126) {
                    f.write(payload.length);
                } else if (payload.length < 65536) {
                    f.write(126);
                    f.write(payload.length >> 8);
                    f.write(payload.length);
                } else {
                    f.write(127);
                    long l = payload.length;
                    for (int i = 7; i >= 0; i--) {
                        f.write((int) (l >> (8 * i)));
                    }
                }
                f.write(payload);
                out.write(f.toByteArray());
                out.flush();
            } catch (SocketException e) {
                throw new IOException("socket closed", e);
            }
        }
    }
}
