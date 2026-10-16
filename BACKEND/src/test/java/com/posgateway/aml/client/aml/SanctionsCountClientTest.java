package com.posgateway.aml.client.aml;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the "sanctionsData DOWN although aml-ms /count returns 6" finding: the count probe
 * has its own timeout (not the 400 ms screening read timeout), sends the internal key, and maps
 * every failure to -1 instead of throwing.
 */
class SanctionsCountClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private AmlMicroserviceProperties start(int status, String body, long delayMs, AtomicReference<String> authSeen)
            throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(SanctionsCountClient.COUNT_PATH, ex -> {
            authSeen.set(ex.getRequestHeaders().getFirst("X-Internal-Auth"));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        AmlMicroserviceProperties p = new AmlMicroserviceProperties();
        p.setEnabled(true);
        p.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        p.setInternalAuthKey("k-123");
        p.setConnectTimeoutMs(500);
        p.setReadTimeoutMs(400);
        p.setCountTimeoutMs(3000);
        return p;
    }

    @Test
    void slowerThanScreeningTimeoutStillReturnsCountAndSendsInternalKey() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AmlMicroserviceProperties p = start(200, "{\"count\":6,\"status\":\"UP\"}", 900, auth);

        assertThat(new SanctionsCountClient(p).getCount()).isEqualTo(6L);
        assertThat(auth.get()).isEqualTo("k-123");
    }

    @Test
    void unavailableAnswerMapsToMinusOne() throws Exception {
        AmlMicroserviceProperties p = start(503, "{\"count\":-1,\"status\":\"UNAVAILABLE\"}", 0,
                new AtomicReference<>());
        assertThat(new SanctionsCountClient(p).getCount()).isEqualTo(-1L);
    }

    @Test
    void timeoutBeyondCountTimeoutMapsToMinusOne() throws Exception {
        AmlMicroserviceProperties p = start(200, "{\"count\":6}", 1500, new AtomicReference<>());
        p.setCountTimeoutMs(300);
        p.setReadTimeoutMs(200);
        assertThat(new SanctionsCountClient(p).getCount()).isEqualTo(-1L);
    }

    @Test
    void disabledMicroserviceIsUnknownAndCountTimeoutNeverBelowReadTimeout() {
        AmlMicroserviceProperties p = new AmlMicroserviceProperties();
        assertThat(new SanctionsCountClient(p).getCount()).isEqualTo(-1L);
        p.setReadTimeoutMs(5000);
        p.setCountTimeoutMs(1000);
        assertThat(SanctionsCountClient.countTimeoutMs(p)).isEqualTo(5000);
    }
}
