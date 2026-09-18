package com.posgateway.aml.service.document;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClamAvDocumentScannerTest {

    @Test
    void streamsBytesUsingClamdInstreamAndParsesCleanResponse() throws Exception {
        byte[] document = "%PDF-1.7\ntest".getBytes(StandardCharsets.US_ASCII);
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<byte[]> received = receiveOneScan(server, "stream: OK\0");
            ClamAvDocumentScanner scanner = new ClamAvDocumentScanner(
                    true, true, "127.0.0.1", server.getLocalPort(), 2000);

            ClamAvDocumentScanner.ScanOutcome outcome = scanner.scan(document);

            assertEquals("CLEAN", outcome.status());
            assertEquals("CLAMAV", outcome.engine());
            assertEquals(true, Arrays.equals(document, received.get()));
        }
    }

    @Test
    void returnsThreatSignatureFromInfectedResponse() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<byte[]> received = receiveOneScan(server, "stream: Eicar-Signature FOUND\0");
            ClamAvDocumentScanner scanner = new ClamAvDocumentScanner(
                    true, true, "127.0.0.1", server.getLocalPort(), 2000);

            ClamAvDocumentScanner.ScanOutcome outcome = scanner.scan(new byte[]{1, 2, 3});

            assertEquals("INFECTED", outcome.status());
            assertEquals("Eicar-Signature", outcome.threatName());
            received.get();
        }
    }

    private CompletableFuture<byte[]> receiveOneScan(ServerSocket server, String response) {
        return CompletableFuture.supplyAsync(() -> {
            try (var socket = server.accept()) {
                DataInputStream input = new DataInputStream(socket.getInputStream());
                byte[] command = input.readNBytes("zINSTREAM\0".length());
                assertEquals("zINSTREAM\0", new String(command, StandardCharsets.US_ASCII));
                var content = new java.io.ByteArrayOutputStream();
                int length;
                while ((length = input.readInt()) != 0) {
                    content.write(input.readNBytes(length));
                }
                socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                return content.toByteArray();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
