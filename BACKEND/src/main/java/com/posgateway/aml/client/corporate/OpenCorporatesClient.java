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
import java.util.Locale;
import java.util.Map;

@Component
public class OpenCorporatesClient {
    private static final String PROVIDER = "OPENCORPORATES";

    private final boolean enabled;
    private final String apiToken;
    private final Duration timeout;
    private final WebClient webClient;

    public OpenCorporatesClient(
            @Value("${corporate.registry.enabled:false}") boolean enabled,
            @Value("${corporate.registry.base-url:https://api.opencorporates.com}") String baseUrl,
            @Value("${corporate.registry.api-token:}") String apiToken,
            @Value("${corporate.registry.timeout:5s}") Duration timeout) {
        if (enabled && !baseUrl.startsWith("https://")) {
            throw new IllegalStateException("Corporate registry integration must use HTTPS");
        }
        this.enabled = enabled;
        this.apiToken = apiToken;
        this.timeout = timeout;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
                .responseTimeout(timeout);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public RegistrySearchResult search(String legalName, String countryCode, String registrationNumber) {
        if (!enabled) return RegistrySearchResult.unavailable("Corporate registry integration is disabled");
        if (apiToken == null || apiToken.isBlank()) {
            return RegistrySearchResult.unavailable("Corporate registry API token is not configured");
        }
        if (legalName == null || legalName.isBlank()) {
            return RegistrySearchResult.unavailable("Merchant legal name is missing");
        }

        String jurisdiction = iso2(countryCode);
        String query = registrationNumber == null || registrationNumber.isBlank()
                ? legalName.trim()
                : legalName.trim() + " " + registrationNumber.trim();
        try {
            CompanyCandidate exact = exactCompany(jurisdiction, registrationNumber);
            if (exact != null) {
                Map<String, Object> provenance = new LinkedHashMap<>();
                provenance.put("provider", PROVIDER);
                provenance.put("apiVersion", "0.4");
                provenance.put("lookupMethod", "JURISDICTION_AND_COMPANY_NUMBER");
                provenance.put("jurisdiction", jurisdiction);
                provenance.put("companyNumber", registrationNumber);
                provenance.put("fetchedAt", Instant.now().toString());
                return new RegistrySearchResult(true, PROVIDER, List.of(exact), provenance, null);
            }
            JsonNode root = webClient.get()
                    .uri(builder -> {
                        var uri = builder.path("/v0.4/companies/search")
                                .queryParam("q", query)
                                .queryParam("order", "score")
                                .queryParam("per_page", 10)
                                .queryParam("api_token", apiToken);
                        if (jurisdiction != null) uri.queryParam("jurisdiction_code", jurisdiction);
                        return uri.build();
                    })
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout.plusMillis(250));
            if (root == null || root.path("results").isMissingNode()) {
                return RegistrySearchResult.unavailable("Corporate registry returned an invalid response");
            }

            List<CompanyCandidate> candidates = new ArrayList<>();
            JsonNode companies = root.path("results").path("companies");
            if (companies.isArray()) {
                for (JsonNode wrapper : companies) {
                    candidates.add(candidate(wrapper.path("company")));
                }
            }
            Map<String, Object> provenance = new LinkedHashMap<>();
            provenance.put("provider", PROVIDER);
            provenance.put("apiVersion", "0.4");
            provenance.put("query", query);
            provenance.put("jurisdiction", jurisdiction);
            provenance.put("totalCount", root.path("results").path("total_count").asInt(candidates.size()));
            provenance.put("fetchedAt", Instant.now().toString());
            return new RegistrySearchResult(true, PROVIDER, List.copyOf(candidates), provenance, null);
        } catch (Exception exception) {
            return RegistrySearchResult.unavailable("Corporate registry request failed");
        }
    }

    private CompanyCandidate exactCompany(String jurisdiction, String registrationNumber) {
        if (jurisdiction == null || registrationNumber == null || registrationNumber.isBlank()) return null;
        try {
            JsonNode root = webClient.get()
                    .uri(builder -> builder.pathSegment("v0.4", "companies", jurisdiction, registrationNumber.trim())
                            .queryParam("api_token", apiToken)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout.plusMillis(250));
            JsonNode company = root == null ? null : root.path("results").path("company");
            return company == null || company.isMissingNode() || company.isNull() ? null : candidate(company);
        } catch (Exception ignored) {
            return null;
        }
    }

    private CompanyCandidate candidate(JsonNode company) {
        return new CompanyCandidate(
                text(company, "name"),
                text(company, "company_number"),
                text(company, "jurisdiction_code"),
                text(company, "current_status"),
                text(company, "incorporation_date"),
                text(company, "registered_address_in_full"),
                safeUrl(firstNonBlank(text(company, "registry_url"), text(company, "opencorporates_url"))));
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

    private String iso2(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) return null;
        String normalized = countryCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() == 2) return normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() != 3) return null;
        for (String iso2 : Locale.getISOCountries()) {
            Locale locale = new Locale.Builder().setRegion(iso2).build();
            try {
                if (normalized.equals(locale.getISO3Country())) return iso2.toLowerCase(Locale.ROOT);
            } catch (Exception ignored) {
                // Continue through the ISO country table.
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    public record CompanyCandidate(
            String name,
            String companyNumber,
            String jurisdictionCode,
            String currentStatus,
            String incorporationDate,
            String registeredAddress,
            String sourceUrl) {
        public Map<String, Object> asEvidence() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", name);
            value.put("companyNumber", companyNumber);
            value.put("jurisdictionCode", jurisdictionCode);
            value.put("currentStatus", currentStatus);
            value.put("incorporationDate", incorporationDate);
            value.put("registeredAddress", registeredAddress);
            value.put("sourceUrl", sourceUrl);
            return value;
        }
    }

    public record RegistrySearchResult(
            boolean available,
            String provider,
            List<CompanyCandidate> candidates,
            Map<String, Object> provenance,
            String unavailableReason) {
        public static RegistrySearchResult unavailable(String reason) {
            return new RegistrySearchResult(false, PROVIDER, List.of(), Map.of(), reason);
        }
    }
}
