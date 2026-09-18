package com.posgateway.aml.client.blockchain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;

@Component
public class TravelRuleGatewayClient {
    private final boolean enabled;
    private final Duration timeout;
    private final String transmissionPath;
    private final WebClient webClient;

    public TravelRuleGatewayClient(
            @Value("${travel-rule.gateway.enabled:false}") boolean enabled,
            @Value("${travel-rule.gateway.base-url:http://localhost:8100}") String baseUrl,
            @Value("${travel-rule.gateway.transmission-path:/v1/transfers}") String transmissionPath,
            @Value("${travel-rule.gateway.api-key:}") String apiKey,
            @Value("${travel-rule.gateway.timeout:5s}") Duration timeout) {
        if (enabled && !baseUrl.startsWith("https://")
                && !baseUrl.startsWith("http://localhost") && !baseUrl.startsWith("http://127.0.0.1")) {
            throw new IllegalStateException("Travel Rule gateway must use HTTPS outside local development");
        }
        this.enabled = enabled;
        this.timeout = timeout;
        this.transmissionPath = transmissionPath;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis()).responseTimeout(timeout);
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) builder.defaultHeader("X-API-Key", apiKey);
        this.webClient = builder.build();
    }

    public TransmissionResult transmit(String idempotencyKey, String transactionReference,
            String protocol, String destinationVasp, Map<String, Object> payload) {
        if (!enabled) return TransmissionResult.unavailable("Travel Rule gateway is not enabled");
        try {
            GatewayResponse response = webClient.post().uri(transmissionPath)
                    .header("Idempotency-Key", idempotencyKey)
                    .bodyValue(new GatewayRequest(transactionReference, protocol, destinationVasp, payload))
                    .retrieve().bodyToMono(GatewayResponse.class).block(timeout.plusMillis(250));
            if (response == null) return TransmissionResult.failed("EMPTY_RESPONSE", "Gateway returned no response");
            if (!response.accepted()) return TransmissionResult.failed(response.responseCode(), response.error());
            return new TransmissionResult(true, true, response.acknowledged(), response.messageId(),
                    response.responseCode(), null);
        } catch (Exception exception) {
            return TransmissionResult.failed("REQUEST_FAILED", "Travel Rule gateway request failed");
        }
    }

    public record TransmissionResult(boolean available, boolean accepted, boolean acknowledged,
            String messageId, String responseCode, String failureReason) {
        static TransmissionResult unavailable(String reason) {
            return new TransmissionResult(false, false, false, null, "UNAVAILABLE", reason);
        }
        static TransmissionResult failed(String code, String reason) {
            return new TransmissionResult(true, false, false, null, code, reason);
        }
    }

    private record GatewayRequest(String transactionReference, String protocol,
            String destinationVasp, Map<String, Object> payload) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GatewayResponse(boolean accepted, boolean acknowledged,
            String messageId, String responseCode, String error) {}
}
