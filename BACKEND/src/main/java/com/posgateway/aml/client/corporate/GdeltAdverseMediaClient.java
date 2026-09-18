package com.posgateway.aml.client.corporate;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GdeltAdverseMediaClient {
    private static final String PROVIDER = "GDELT_DOC_2";

    private final boolean enabled;
    private final Duration timeout;
    private final String riskTerms;
    private final WebClient webClient;

    public GdeltAdverseMediaClient(
            @Value("${adverse.media.enabled:false}") boolean enabled,
            @Value("${adverse.media.base-url:https://api.gdeltproject.org}") String baseUrl,
            @Value("${adverse.media.timeout:8s}") Duration timeout,
            @Value("${adverse.media.risk-terms:fraud OR \"money laundering\" OR corruption OR bribery OR sanctions OR trafficking OR embezzlement OR terrorism OR scam OR investigation OR charged OR convicted}") String riskTerms) {
        if (enabled && !baseUrl.startsWith("https://")) {
            throw new IllegalStateException("Adverse-media integration must use HTTPS");
        }
        this.enabled = enabled;
        this.timeout = timeout;
        this.riskTerms = riskTerms;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
                .responseTimeout(timeout);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public AdverseMediaResult search(String legalName, String tradingName) {
        if (!enabled) return AdverseMediaResult.unavailable("Adverse-media integration is disabled");
        if (legalName == null || legalName.isBlank()) {
            return AdverseMediaResult.unavailable("Merchant legal name is missing");
        }
        String subject = tradingName == null || tradingName.isBlank()
                ? quote(legalName.trim())
                : "(" + quote(legalName.trim()) + " OR " + quote(tradingName.trim()) + ")";
        String query = subject + " (" + riskTerms + ")";
        try {
            JsonNode root = webClient.get()
                    .uri(builder -> builder.path("/api/v2/doc/doc")
                            .queryParam("query", query)
                            .queryParam("mode", "ArtList")
                            .queryParam("format", "json")
                            .queryParam("maxrecords", 50)
                            .queryParam("timespan", "1y")
                            .queryParam("sort", "HybridRel")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout.plusMillis(250));
            if (root == null || !root.has("articles")) {
                return AdverseMediaResult.unavailable("Adverse-media provider returned an invalid response");
            }
            List<Article> articles = new ArrayList<>();
            JsonNode values = root.path("articles");
            if (values.isArray()) {
                for (JsonNode article : values) {
                    String url = safeUrl(text(article, "url"));
                    String title = text(article, "title");
                    if (url == null || title == null) continue;
                    articles.add(new Article(title, url, text(article, "domain"), text(article, "seendate"),
                            text(article, "language"), text(article, "sourcecountry")));
                }
            }
            Map<String, Object> provenance = new LinkedHashMap<>();
            provenance.put("provider", PROVIDER);
            provenance.put("query", query);
            provenance.put("timespan", "1y");
            provenance.put("maximumRecords", 50);
            provenance.put("fetchedAt", Instant.now().toString());
            return new AdverseMediaResult(true, PROVIDER, query, List.copyOf(articles), provenance, null);
        } catch (Exception exception) {
            return AdverseMediaResult.unavailable("Adverse-media request failed");
        }
    }

    private String quote(String value) {
        return "\"" + value.replace("\"", "") + "\"";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private String safeUrl(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String scheme = URI.create(value).getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme) ? value : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record Article(String title, String url, String domain, String seenDate,
                          String language, String sourceCountry) {
        public Map<String, Object> asEvidence() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("title", title);
            value.put("url", url);
            value.put("domain", domain);
            value.put("seenDate", seenDate);
            value.put("language", language);
            value.put("sourceCountry", sourceCountry);
            return value;
        }
    }

    public record AdverseMediaResult(
            boolean available,
            String provider,
            String query,
            List<Article> articles,
            Map<String, Object> provenance,
            String unavailableReason) {
        public static AdverseMediaResult unavailable(String reason) {
            return new AdverseMediaResult(false, PROVIDER, null, List.of(), Map.of(), reason);
        }
    }
}
