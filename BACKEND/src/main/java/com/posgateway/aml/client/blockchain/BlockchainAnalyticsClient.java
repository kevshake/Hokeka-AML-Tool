package com.posgateway.aml.client.blockchain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Vendor-neutral adapter for a normalized blockchain-intelligence gateway. */
@Component
public class BlockchainAnalyticsClient {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainAnalyticsClient.class);

    private final boolean enabled;
    private final String provider;
    private final String screeningPath;
    private final Duration timeout;
    private final WebClient webClient;

    public BlockchainAnalyticsClient(
            @Value("${blockchain.analytics.enabled:false}") boolean enabled,
            @Value("${blockchain.analytics.provider:UNCONFIGURED}") String provider,
            @Value("${blockchain.analytics.base-url:http://localhost:8099}") String baseUrl,
            @Value("${blockchain.analytics.screening-path:/v1/wallets/screen}") String screeningPath,
            @Value("${blockchain.analytics.api-key:}") String apiKey,
            @Value("${blockchain.analytics.timeout:3s}") Duration timeout) {
        if (enabled && !baseUrl.startsWith("https://")
                && !baseUrl.startsWith("http://localhost") && !baseUrl.startsWith("http://127.0.0.1")) {
            throw new IllegalStateException("Blockchain analytics gateway must use HTTPS outside local development");
        }
        this.enabled = enabled;
        this.provider = provider;
        this.screeningPath = screeningPath;
        this.timeout = timeout;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
                .responseTimeout(timeout);
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-API-Key", apiKey);
        }
        this.webClient = builder.build();
    }

    public WalletRiskResult screenWallet(String address, String network) {
        if (address == null || address.isBlank()) {
            return WalletRiskResult.unavailable(provider, "Wallet address is missing");
        }
        if (!enabled) {
            return WalletRiskResult.unavailable(provider, "Blockchain analytics provider is not enabled");
        }

        try {
            ProviderResponse response = webClient.post()
                    .uri(screeningPath)
                    .bodyValue(new ProviderRequest(address, network))
                    .retrieve()
                    .bodyToMono(ProviderResponse.class)
                    .block(timeout.plusMillis(250));
            if (response == null || response.riskScore() == null) {
                return WalletRiskResult.unavailable(provider, "Provider returned no risk score");
            }
            int boundedScore = Math.max(0, Math.min(100, response.riskScore()));
            return new WalletRiskResult(true, provider, boundedScore,
                    response.categories() != null ? response.categories() : List.of(),
                    response.reference(), response.directExposurePercent(), response.indirectExposurePercent(),
                    response.maximumExposureDepth(), response.attributions() != null ? response.attributions() : List.of(), null);
        } catch (Exception e) {
            logger.error("Blockchain wallet screening failed for network {}: {}", network, e.getMessage());
            return WalletRiskResult.unavailable(provider, "Provider request failed");
        }
    }

    public record WalletRiskResult(
            boolean available,
            String provider,
            int riskScore,
            List<String> categories,
            String providerReference,
            BigDecimal directExposurePercent,
            BigDecimal indirectExposurePercent,
            Integer maximumExposureDepth,
            List<Map<String, Object>> attributions,
            String unavailableReason) {
        public static WalletRiskResult unavailable(String provider, String reason) {
            return new WalletRiskResult(false, provider, 0, List.of(), null,
                    null, null, null, List.of(), reason);
        }
    }

    private record ProviderRequest(String address, String network) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProviderResponse(Integer riskScore, List<String> categories, String reference,
            BigDecimal directExposurePercent, BigDecimal indirectExposurePercent,
            Integer maximumExposureDepth, List<Map<String, Object>> attributions) {}
}
