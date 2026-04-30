package com.posgateway.aml.service.aml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.model.ScreeningResult.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Sumsub AML Service (Tier 1)
 * Used for comprehensive screening of NEW merchants
 * 
 * Features:
 * - Sanctions screening (OFAC, UN, EU, etc.)
 * - PEP screening (current, former, RCA)
 * - Adverse media screening
 * 
 * Cost: ~$1.85 per check
 */
// @RequiredArgsConstructor removed
@Service
public class SumsubAmlService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SumsubAmlService.class);

    @Value("${sumsub.enabled:false}")
    private boolean enabled;

    @Value("${sumsub.api.url:https://api.sumsub.com/resources}")
    private String apiUrl;

    @Value("${sumsub.api.key}")
    private String apiKey;

    @Value("${sumsub.api.secret}")
    private String apiSecret;

    @Value("${sumsub.cost.per.check:1.85}")
    private double costPerCheck;

    private final ObjectMapper objectMapper;
    private final AerospikeSanctionsScreeningService aerospikeService;

    public SumsubAmlService(ObjectMapper objectMapper, AerospikeSanctionsScreeningService aerospikeService) {
        this.objectMapper = objectMapper;
        this.aerospikeService = aerospikeService;
    }
    // Fallback

    /**
     * Screen merchant via Sumsub API
     * Includes sanctions, PEP, and adverse media
     */
    @CircuitBreaker(name = "sumsub", fallbackMethod = "fallbackScreenMerchant")
    @Retry(name = "sumsub")
    @io.github.resilience4j.ratelimiter.annotation.RateLimiter(name = "sumsub")
    @org.springframework.cache.annotation.Cacheable(value = "screeningResults", key = "#merchant.merchantId")
    public ScreeningResult screenMerchantWithSumsub(Merchant merchant) {
        if (!enabled) {
            log.warn("Sumsub is disabled, using fallback");
            return fallbackScreenMerchant(merchant, new RuntimeException("Sumsub disabled"));
        }

        log.info("Screening merchant '{}' via Sumsub", merchant.getLegalName());

        try {
            // Build request payload
            JsonNode requestPayload = buildMerchantRequest(merchant);

            // Call Sumsub API
            Response response = callSumsubApi("/screenings/merchants", requestPayload);

            // Parse response
            ScreeningResult result = parseSumsubResponse(response, merchant.getLegalName());
            result.setScreeningProvider("SUMSUB");

            log.info("Sumsub screening complete: {} (status: {})", merchant.getLegalName(), result.getStatus());

            return result;

        } catch (Exception e) {
            log.error("Sumsub screening failed for merchant '{}': {}", merchant.getLegalName(), e.getMessage());
            throw new RuntimeException("Sumsub API error", e);
        }
    }

    /**
     * Batch screen merchants concurrently
     */
    @org.springframework.scheduling.annotation.Async
    public java.util.concurrent.CompletableFuture<java.util.List<ScreeningResult>> screenBatch(
            java.util.List<Merchant> merchants) {
        log.info("Batch screening {} merchants", merchants.size());
        java.util.List<ScreeningResult> results = merchants.parallelStream()
                .map(this::screenMerchantWithSumsub)
                .collect(java.util.stream.Collectors.toList());
        return java.util.concurrent.CompletableFuture.completedFuture(results);
    }

    /**
     * Screen beneficial owner via Sumsub
     */
    @CircuitBreaker(name = "sumsub", fallbackMethod = "fallbackScreenBeneficialOwner")
    @Retry(name = "sumsub")
    public ScreeningResult screenBeneficialOwnerWithSumsub(BeneficialOwner owner) {
        if (!enabled) {
            return fallbackScreenBeneficialOwner(owner, new RuntimeException("Sumsub disabled"));
        }

        log.info("Screening beneficial owner '{}' via Sumsub", owner.getFullName());

        try {
            JsonNode requestPayload = buildBeneficialOwnerRequest(owner);
            Response response = callSumsubApi("/screenings/individuals", requestPayload);
            ScreeningResult result = parseSumsubResponse(response, owner.getFullName());
            result.setScreeningProvider("SUMSUB");

            return result;

        } catch (Exception e) {
            log.error("Sumsub screening failed for UBO '{}': {}", owner.getFullName(), e.getMessage());
            throw new RuntimeException("Sumsub API error", e);
        }
    }

    /**
     * Fallback to Aerospike screening if Sumsub fails
     */
    private ScreeningResult fallbackScreenMerchant(Merchant merchant, Throwable t) {
        log.warn("Falling back to Aerospike for merchant '{}'", merchant.getLegalName());
        return aerospikeService.screenMerchant(merchant.getLegalName(), merchant.getTradingName());
    }

    private ScreeningResult fallbackScreenBeneficialOwner(BeneficialOwner owner, Throwable t) {
        log.warn("Falling back to Aerospike for UBO '{}'", owner.getFullName());
        return aerospikeService.screenBeneficialOwner(owner.getFullName(), owner.getDateOfBirth());
    }

    /**
     * Build Sumsub request for merchant
     */
    private JsonNode buildMerchantRequest(Merchant merchant) {
        try {
            String json = String.format("""
                    {
                        "externalUserId": "%s",
                        "info": {
                            "companyName": "%s",
                            "tradingName": "%s",
                            "country": "%s",
                            "registrationNumber": "%s",
                            "type": "ORGANIZATION"
                        }
                    }
                    """,
                    merchant.getMerchantId(),
                    merchant.getLegalName(),
                    merchant.getTradingName() != null ? merchant.getTradingName() : "",
                    merchant.getCountry(),
                    merchant.getRegistrationNumber());

            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request", e);
        }
    }

    /**
     * Build Sumsub request for beneficial owner
     */
    private JsonNode buildBeneficialOwnerRequest(BeneficialOwner owner) {
        try {
            String json = String.format("""
                    {
                        "externalUserId": "%s",
                        "info": {
                            "firstName": "%s",
                            "lastName": "",
                            "dob": "%s",
                            "country": "%s",
                            "nationality": "%s",
                            "type": "INDIVIDUAL"
                        }
                    }
                    """,
                    owner.getOwnerId(),
                    owner.getFullName(),
                    owner.getDateOfBirth().toString(),
                    owner.getCountryOfResidence(),
                    owner.getNationality());

            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request", e);
        }
    }

    /**
     * Call Sumsub API with authentication
     */
    private Response callSumsubApi(String endpoint, JsonNode payload) {
        try {
            String url = apiUrl + endpoint;
            long timestamp = System.currentTimeMillis() / 1000;
            String method = "POST";

            // Generate signature
            String signature = generateSignature(method, endpoint, timestamp, payload.toString());

            // Make API call
            Response response = RestAssured.given()
                    .header("Content-Type", "application/json")
                    .header("X-App-Token", apiKey)
                    .header("X-App-Access-Sig", signature)
                    .header("X-App-Access-Ts", String.valueOf(timestamp))
                    .body(payload.toString())
                    .post(url);

            if (response.getStatusCode() != 200 && response.getStatusCode() != 201) {
                log.error("Sumsub API error: {} - {}", response.getStatusCode(), response.getBody().asString());
                throw new RuntimeException("Sumsub API returned status " + response.getStatusCode());
            }

            return response;

        } catch (Exception e) {
            throw new RuntimeException("Sumsub API call failed", e);
        }
    }

    /**
     * Generate HMAC signature for Sumsub authentication
     */
    private String generateSignature(String method, String endpoint, long timestamp, String body) {
        try {
            String message = timestamp + method + endpoint + body;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * Parse Sumsub API response into ScreeningResult
     */
    private ScreeningResult parseSumsubResponse(Response response, String screenedName) {
        try {
            JsonNode json = objectMapper.readTree(response.getBody().asString());

            List<Match> matches = new ArrayList<>();
            ScreeningStatus status = ScreeningStatus.CLEAR;

            // Parse sanctions matches
            if (json.has("sanctions") && json.get("sanctions").isArray()) {
                for (JsonNode sanctionNode : json.get("sanctions")) {
                    Match match = parseSanctionMatch(sanctionNode);
                    matches.add(match);
                }
            }

            // Parse PEP matches
            if (json.has("pep") && json.get("pep").isArray()) {
                for (JsonNode pepNode : json.get("pep")) {
                    Match match = parsePepMatch(pepNode);
                    matches.add(match);
                }
            }

            // Parse adverse media
            if (json.has("adverseMedia") && json.get("adverseMedia").isArray()) {
                for (JsonNode mediaNode : json.get("adverseMedia")) {
                    Match match = parseAdverseMediaMatch(mediaNode);
                    matches.add(match);
                }
            }

            // Determine status
            if (!matches.isEmpty()) {
                status = matches.stream().anyMatch(m -> m.getSimilarityScore() >= 0.95)
                        ? ScreeningStatus.MATCH
                        : ScreeningStatus.POTENTIAL_MATCH;
            }

            return ScreeningResult.builder()
                    .screenedName(screenedName)
                    .status(status)
                    .matchCount(matches.size())
                    .highestMatchScore(
                            matches.stream().map(Match::getSimilarityScore).max(Double::compareTo).orElse(0.0))
                    .matches(matches)
                    .screenedAt(LocalDateTime.now())
                    .screeningProvider("SUMSUB")
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Sumsub response: {}", e.getMessage());
            throw new RuntimeException("Response parsing failed", e);
        }
    }

    private Match parseSanctionMatch(JsonNode node) {
        return Match.builder()
                .matchedName(node.get("name").asText())
                .similarityScore(node.has("score") ? node.get("score").asDouble() : 1.0)
                .listName(node.get("listName").asText())
                .entityType(EntityType.valueOf(node.get("type").asText()))
                .matchType(MatchType.NAME_MATCH)
                .sanctionType(node.has("sanctionType") ? node.get("sanctionType").asText() : null)
                .build();
    }

    private Match parsePepMatch(JsonNode node) {
        return Match.builder()
                .matchedName(node.get("name").asText())
                .similarityScore(node.has("score") ? node.get("score").asDouble() : 1.0)
                .listName("PEP")
                .entityType(EntityType.PERSON)
                .matchType(MatchType.NAME_MATCH)
                .pepLevel(node.has("level") ? node.get("level").asText() : null)
                .position(node.has("position") ? node.get("position").asText() : null)
                .build();
    }

    private Match parseAdverseMediaMatch(JsonNode node) {
        return Match.builder()
                .matchedName(node.get("name").asText())
                .similarityScore(0.9) // Adverse media has lower confidence
                .listName("ADVERSE_MEDIA")
                .entityType(EntityType.PERSON)
                .matchType(MatchType.NAME_MATCH)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getCostPerCheck() {
        return costPerCheck;
    }
}
