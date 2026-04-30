package com.posgateway.aml.service.aml;

import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.model.ScreeningResult.EntityType;
import com.posgateway.aml.model.ScreeningResult.ScreeningStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Stub kept after Aerospike removal.
 *
 * <p>The original implementation screened names against an Aerospike-backed
 * sanctions database. That data now lives in (or is reached via) the AML
 * microservice. Until the microservice exposes a {@code /internal/v1/aml/screen}
 * endpoint, this stub returns {@link ScreeningStatus#CLEAR} for every request
 * so callers continue to compile and the overall pipeline fails open.
 *
 * <p>TODO(aerospike-removal): replace these methods with HTTP calls into
 * {@link com.posgateway.aml.client.aml.AmlMicroserviceClient} once the screening
 * endpoint exists, OR have callers directly use {@code SumsubAmlService} as the
 * sole screening provider.
 */
@Service
public class AerospikeSanctionsScreeningService {

    private static final Logger log = LoggerFactory.getLogger(AerospikeSanctionsScreeningService.class);

    public ScreeningResult screenName(String name, EntityType entityType) {
        log.debug("AerospikeSanctionsScreeningService stub — returning CLEAR for '{}'", name);
        return ScreeningResult.builder()
                .screenedName(name != null ? name : "")
                .entityType(entityType != null ? entityType : EntityType.PERSON)
                .status(ScreeningStatus.CLEAR)
                .matchCount(0)
                .highestMatchScore(0.0)
                .matches(new ArrayList<>())
                .screenedAt(LocalDateTime.now())
                .screeningProvider("AEROSPIKE_STUB")
                .build();
    }

    public ScreeningResult screenMerchant(String legalName, String tradingName) {
        return screenName(legalName, EntityType.ORGANIZATION);
    }

    public ScreeningResult screenBeneficialOwner(String fullName, LocalDate dateOfBirth) {
        return screenName(fullName, EntityType.PERSON);
    }
}
