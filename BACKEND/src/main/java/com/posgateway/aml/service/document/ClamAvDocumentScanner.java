package com.posgateway.aml.service.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** Streams document bytes to clamd using its framed INSTREAM protocol. */
@Service
public class ClamAvDocumentScanner {

    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK_SIZE = 8192;

    private final boolean enabled;
    private final boolean required;
    private final String host;
    private final int port;
    private final int timeoutMillis;

    public ClamAvDocumentScanner(
            @Value("${app.document.antivirus.enabled:false}") boolean enabled,
            @Value("${app.document.antivirus.required:false}") boolean required,
            @Value("${app.document.antivirus.host:localhost}") String host,
            @Value("${app.document.antivirus.port:3310}") int port,
            @Value("${app.document.antivirus.timeout-ms:10000}") int timeoutMillis) {
        this.enabled = enabled;
        this.required = required;
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    public ScanOutcome scan(byte[] content) {
        if (!enabled) {
            if (required) {
                throw new IllegalStateException("Document malware scanning is required but not enabled");
            }
            return new ScanOutcome("NOT_SCANNED", null, null, null);
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);

            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write(INSTREAM_COMMAND);
            for (int offset = 0; offset < content.length; offset += CHUNK_SIZE) {
                int length = Math.min(CHUNK_SIZE, content.length - offset);
                output.writeInt(length);
                output.write(content, offset, length);
            }
            output.writeInt(0);
            output.flush();

            String response = readNullTerminated(socket.getInputStream()).trim();
            LocalDateTime scannedAt = LocalDateTime.now();
            if (response.endsWith(": OK")) {
                return new ScanOutcome("CLEAN", "CLAMAV", null, scannedAt);
            }
            if (response.endsWith(" FOUND")) {
                int separator = response.indexOf(": ");
                String threat = separator >= 0
                        ? response.substring(separator + 2, response.length() - " FOUND".length()).trim()
                        : "UNKNOWN";
                return new ScanOutcome("INFECTED", "CLAMAV", threat, scannedAt);
            }
            throw new IllegalStateException("ClamAV could not scan the document: " + sanitize(response));
        } catch (IOException ex) {
            throw new IllegalStateException("ClamAV is unavailable; document upload was rejected", ex);
        }
    }

    private String readNullTerminated(InputStream input) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1 && value != 0) {
            if (response.size() >= 4096) {
                throw new IOException("ClamAV response exceeded 4096 bytes");
            }
            response.write(value);
        }
        if (response.size() == 0) {
            throw new IOException("ClamAV returned an empty response");
        }
        return response.toString(StandardCharsets.UTF_8);
    }

    private String sanitize(String value) {
        return value.replaceAll("[\\r\\n\\p{Cntrl}]", " ").strip();
    }

    public record ScanOutcome(String status, String engine, String threatName, LocalDateTime scannedAt) {}
}
