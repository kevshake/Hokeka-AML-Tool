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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin proxy in front of the AML microservice's sanctions screening endpoint.
 *
 * <p>Class name is preserved for binary compatibility with existing callers
 * ({@code SumsubAmlService}, {@code AmlScreeningOrchestrator}, etc.) that wired
 * to the legacy Aerospike-backed implementation. After Aerospike's removal from
 * BACKEND, this delegates to {@link SanctionsScreenClient}; when the client
 * returns {@code null} (microservice disabled / circuit broken), we fail open
 * with a CLEAR result so the rest of the pipeline keeps running.
 */
@Service
public class AerospikeSanctionsScreeningService {

    private static final Logger log = LoggerFactory.getLogger(AerospikeSanctionsScreeningService.class);
    private static final String PROVIDER = "AML_MICROSERVICE";
    private static final String PROVIDER_UNAVAILABLE = "AML_MICROSERVICE_UNAVAILABLE";

    @Autowired
    private SanctionsScreenClient sanctionsScreenClient;

    public ScreeningResult screenName(String name, EntityType entityType) {
        EntityType resolvedType = entityType != null ? entityType : EntityType.PERSON;
        String typeStr = mapTypeToWire(resolvedType);

        BackendSanctionsScreenResponse resp = sanctionsScreenClient.screen(
                new BackendSanctionsScreenRequest(name != null ? name : "", typeStr, null));

        if (resp == null) {
            log.debug("Sanctions microservice unavailable — failing open CLEAR for '{}'", name);
            return ScreeningResult.builder()
                    .screenedName(name != null ? name : "")
                    .entityType(resolvedType)
                    .status(ScreeningStatus.CLEAR)
                    .matchCount(0)
                    .highestMatchScore(0.0)
                    .matches(new ArrayList<>())
                    .screenedAt(LocalDateTime.now())
                    .screeningProvider(PROVIDER_UNAVAILABLE)
                    .build();
        }

        List<Match> matches = new ArrayList<>();
        double top = 0.0;
        if (resp.matches() != null) {
            for (BackendSanctionsScreenResponse.MatchDto m : resp.matches()) {
                if (m.similarityScore() > top) top = m.similarityScore();
                matches.add(Match.builder()
                        .matchedName(m.matchedName())
                        .similarityScore(m.similarityScore())
                        .listName(m.listName())
                        .entityType(resolvedType)
                        .matchType(MatchType.NAME_MATCH)
                        .sanctionType("Sanctions match")
                        .build());
            }
        }

        return ScreeningResult.builder()
                .screenedName(name != null ? name : "")
                .entityType(resolvedType)
                .status(mapStatus(resp.status()))
                .matchCount(matches.size())
                .highestMatchScore(top)
                .matches(matches)
                .screenedAt(resp.checkedAt() != null
                        ? LocalDateTime.ofInstant(resp.checkedAt(), ZoneId.systemDefault())
                        : LocalDateTime.now())
                .screeningProvider(PROVIDER)
                .build();
    }

    public ScreeningResult screenMerchant(String legalName, String tradingName) {
        return screenName(legalName, EntityType.ORGANIZATION);
    }

    public ScreeningResult screenBeneficialOwner(String fullName, LocalDate dateOfBirth) {
        return screenName(fullName, EntityType.PERSON);
    }

    private static String mapTypeToWire(EntityType t) {
        if (t == null) return null;
        return switch (t) {
            case PERSON -> "PERSON";
            case ORGANIZATION -> "ORGANIZATION";
            default -> null;
        };
    }

    private static ScreeningStatus mapStatus(String wire) {
        if (wire == null) return ScreeningStatus.CLEAR;
        return switch (wire) {
            case "FLAGGED" -> ScreeningStatus.MATCH;
            case "REVIEW" -> ScreeningStatus.POTENTIAL_MATCH;
            default -> ScreeningStatus.CLEAR;
        };
    }
}
