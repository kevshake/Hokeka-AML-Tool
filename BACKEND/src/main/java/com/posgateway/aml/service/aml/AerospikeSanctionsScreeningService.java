package com.posgateway.aml.service.aml;

import com.posgateway.aml.client.aml.SanctionsScreenClient;
import com.posgateway.aml.client.aml.SanctionsScreenClient.BackendSanctionsScreenRequest;
import com.posgateway.aml.client.aml.SanctionsScreenClient.BackendSanctionsScreenResponse;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.model.ScreeningResult.EntityType;
import com.posgateway.aml.model.ScreeningResult.Match;
import com.posgateway.aml.model.ScreeningResult.MatchType;
import com.posgateway.aml.model.ScreeningResult.ScreeningStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility-named HTTP proxy for sanctions data owned by aml-microservice.
 * BACKEND never connects to Aerospike directly.
 */
@Service
public class AerospikeSanctionsScreeningService {

    private static final Logger log = LoggerFactory.getLogger(AerospikeSanctionsScreeningService.class);
    private static final String PROVIDER = "AML_MICROSERVICE";
    private static final String PROVIDER_UNAVAILABLE = "AML_MICROSERVICE_UNAVAILABLE";

    private final SanctionsScreenClient sanctionsScreenClient;

    public AerospikeSanctionsScreeningService(SanctionsScreenClient sanctionsScreenClient) {
        this.sanctionsScreenClient = sanctionsScreenClient;
    }

    @Cacheable(
            cacheNames = "sanctions",
            key = "(#name != null ? #name.trim().toLowerCase() : '') + ':' + "
                    + "(#entityType != null ? #entityType.name() : 'PERSON')",
            unless = "#result.status == T(com.posgateway.aml.model.ScreeningResult.ScreeningStatus).UNAVAILABLE")
    public ScreeningResult screenName(String name, EntityType entityType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A non-blank name is required for sanctions screening");
        }

        String normalizedName = name.trim();
        EntityType resolvedType = entityType != null ? entityType : EntityType.PERSON;
        BackendSanctionsScreenResponse response = sanctionsScreenClient.screen(
                new BackendSanctionsScreenRequest(normalizedName, mapTypeToWire(resolvedType), null));

        ScreeningStatus status = mapStatus(response.status());
        if (status == ScreeningStatus.UNAVAILABLE) {
            log.error("Sanctions microservice unavailable while screening '{}'", normalizedName);
            return unavailable(normalizedName, resolvedType);
        }

        List<Match> matches = new ArrayList<>();
        double highestScore = 0.0;
        if (response.matches() != null) {
            for (BackendSanctionsScreenResponse.MatchDto match : response.matches()) {
                highestScore = Math.max(highestScore, match.similarityScore());
                matches.add(Match.builder()
                        .matchedName(match.matchedName())
                        .similarityScore(match.similarityScore())
                        .listName(match.listName())
                        .entityType(resolvedType)
                        .matchType(MatchType.NAME_MATCH)
                        .sanctionType(deriveSanctionType(match))
                        .pepLevel(match.pepLevel())
                        .build());
            }
        }

        return ScreeningResult.builder()
                .screenedName(normalizedName)
                .entityType(resolvedType)
                .status(status)
                .matchCount(matches.size())
                .highestMatchScore(highestScore)
                .matches(matches)
                .screenedAt(response.checkedAt() != null
                        ? LocalDateTime.ofInstant(response.checkedAt(), ZoneId.systemDefault())
                        : LocalDateTime.now())
                .screeningProvider(PROVIDER)
                .build();
    }

    /**
     * W14-4 fix: sanctionType used to be the literal string "Sanctions match" for every single
     * match regardless of which list it came from, so two matches on completely different lists
     * (OFAC vs a PEP register) were indistinguishable by sanctionType alone. Derive it from what
     * the wire DTO actually carries: a PEP-level match is labelled as such, otherwise the specific
     * list name is used, falling back to the old generic text only when neither is available.
     */
    private String deriveSanctionType(BackendSanctionsScreenResponse.MatchDto match) {
        if (match.pepLevel() != null && !match.pepLevel().isBlank()) {
            return "PEP match (" + match.pepLevel() + ")";
        }
        if (match.listName() != null && !match.listName().isBlank()) {
            return "Sanctions list match: " + match.listName();
        }
        return "Sanctions match";
    }

    public ScreeningResult screenMerchant(String legalName, String tradingName) {
        ScreeningResult legalResult = screenName(legalName, EntityType.ORGANIZATION);
        if (legalResult.getStatus() == ScreeningStatus.UNAVAILABLE
                || tradingName == null
                || tradingName.isBlank()
                || tradingName.trim().equalsIgnoreCase(legalName.trim())) {
            return legalResult;
        }

        ScreeningResult tradingResult = screenName(tradingName, EntityType.ORGANIZATION);
        if (tradingResult.getStatus() == ScreeningStatus.UNAVAILABLE) {
            return unavailable(legalName.trim(), EntityType.ORGANIZATION);
        }

        List<Match> combinedMatches = new ArrayList<>(legalResult.getMatches());
        combinedMatches.addAll(tradingResult.getMatches());
        ScreeningStatus combinedStatus = moreSevere(legalResult.getStatus(), tradingResult.getStatus());
        return ScreeningResult.builder()
                .screenedName(legalName.trim())
                .entityType(EntityType.ORGANIZATION)
                .status(combinedStatus)
                .matchCount(combinedMatches.size())
                .highestMatchScore(Math.max(
                        legalResult.getHighestMatchScore() != null ? legalResult.getHighestMatchScore() : 0.0,
                        tradingResult.getHighestMatchScore() != null ? tradingResult.getHighestMatchScore() : 0.0))
                .matches(combinedMatches)
                .screenedAt(LocalDateTime.now())
                .screeningProvider(PROVIDER)
                .build();
    }

    public ScreeningResult screenBeneficialOwner(String fullName, LocalDate dateOfBirth) {
        return screenName(fullName, EntityType.PERSON);
    }

    private ScreeningResult unavailable(String name, EntityType entityType) {
        return ScreeningResult.builder()
                .screenedName(name)
                .entityType(entityType)
                .status(ScreeningStatus.UNAVAILABLE)
                .matchCount(0)
                .highestMatchScore(0.0)
                .matches(new ArrayList<>())
                .screenedAt(LocalDateTime.now())
                .screeningProvider(PROVIDER_UNAVAILABLE)
                .build();
    }

    private static String mapTypeToWire(EntityType type) {
        return switch (type) {
            case PERSON -> "PERSON";
            case ORGANIZATION -> "ORGANIZATION";
            case VESSEL -> "VESSEL";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private static ScreeningStatus mapStatus(String wireStatus) {
        if (wireStatus == null) return ScreeningStatus.UNAVAILABLE;
        return switch (wireStatus.toUpperCase()) {
            case "CLEAR" -> ScreeningStatus.CLEAR;
            case "REVIEW" -> ScreeningStatus.POTENTIAL_MATCH;
            case "FLAGGED" -> ScreeningStatus.MATCH;
            case "UNAVAILABLE" -> ScreeningStatus.UNAVAILABLE;
            default -> ScreeningStatus.UNAVAILABLE;
        };
    }

    private static ScreeningStatus moreSevere(ScreeningStatus left, ScreeningStatus right) {
        if (left == ScreeningStatus.UNAVAILABLE || right == ScreeningStatus.UNAVAILABLE) return ScreeningStatus.UNAVAILABLE;
        if (left == ScreeningStatus.MATCH || right == ScreeningStatus.MATCH) return ScreeningStatus.MATCH;
        if (left == ScreeningStatus.POTENTIAL_MATCH || right == ScreeningStatus.POTENTIAL_MATCH) {
            return ScreeningStatus.POTENTIAL_MATCH;
        }
        return ScreeningStatus.CLEAR;
    }
}
