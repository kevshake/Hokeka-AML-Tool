package com.posgateway.aml.controller;

import com.posgateway.aml.client.aml.SanctionsScreenClient;
import com.posgateway.aml.client.aml.SanctionsScreenClient.BackendSanctionsScreenRequest;
import com.posgateway.aml.client.aml.SanctionsScreenClient.BackendSanctionsScreenResponse;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.service.aml.AerospikeSanctionsScreeningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for direct sanctions screening.
 *
 * <p>Sanctions data lives in the AML microservice (Aerospike namespace {@code aml},
 * set {@code sanctions}). This controller delegates the {@code /sanctions/screen}
 * call to {@link SanctionsScreenClient}; the legacy {@code /screen/person} and
 * {@code /screen/organization} routes still go through
 * {@link AerospikeSanctionsScreeningService} (now a thin proxy onto the same client).
 *
 * <p>If the microservice is disabled or its circuit breaker is open, we return
 * {@code status=UNAVAILABLE} with HTTP 200 — graceful degradation, not 5xx.
 */
@RestController
@RequestMapping("/sanctions")
public class SanctionsScreeningController {

    private static final Logger log = LoggerFactory.getLogger(SanctionsScreeningController.class);

    @Autowired
    private SanctionsScreenClient sanctionsScreenClient;

    @Autowired
    private AerospikeSanctionsScreeningService screeningService;

    /**
     * Screen a name against the sanctions database via aml-microservice.
     * POST /api/v1/sanctions/screen
     */
    @PostMapping("/screen")
    public ResponseEntity<Map<String, Object>> screenName(@RequestBody ScreeningRequest request) {
        log.info("Screening name: {}, entityType: {}", request.getName(), request.getEntityType());

        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Name is required");
                return ResponseEntity.badRequest().body(error);
            }

            String name = request.getName().trim();
            String type = request.getEntityType();
            if (type != null) {
                type = type.trim().toUpperCase();
                if (type.isEmpty()) type = null;
            }

            BackendSanctionsScreenResponse resp = sanctionsScreenClient.screen(
                    new BackendSanctionsScreenRequest(name, type, null));

            // Microservice disabled / circuit broken — degrade gracefully.
            if (resp == null) {
                Map<String, Object> body = new HashMap<>();
                body.put("name", name);
                body.put("screenedName", name);
                body.put("status", "UNAVAILABLE");
                body.put("matches", new ArrayList<>());
                body.put("hits", new ArrayList<>());
                body.put("matchFound", false);
                body.put("matchCount", 0);
                body.put("highestMatchScore", 0.0);
                body.put("confidence", 0.0);
                body.put("entityType", type != null ? type : "PERSON");
                body.put("screeningProvider", "AML_MICROSERVICE_UNAVAILABLE");
                return ResponseEntity.ok(body);
            }

            return ResponseEntity.ok(toFrontendShape(name, type, resp));

        } catch (Exception e) {
            log.error("Error screening name '{}': {}", request.getName(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error performing screening: " + e.getMessage());
            error.put("message", "Error performing screening. Please try again.");
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Map the microservice response to the shape the FE ScreeningPage consumes.
     * Keeps backwards-compatibility with callers reading {@code matches},
     * {@code hits}, {@code matchFound}, {@code matchCount}, {@code highestMatchScore},
     * {@code confidence}.
     */
    private Map<String, Object> toFrontendShape(String name, String type, BackendSanctionsScreenResponse resp) {
        Map<String, Object> response = new HashMap<>();
        boolean hasMatches = resp.matches() != null && !resp.matches().isEmpty();
        double topScore = hasMatches ? resp.matches().get(0).similarityScore() : 0.0;

        response.put("name", name);
        response.put("screenedName", name);
        response.put("status", resp.status() != null ? resp.status() : "CLEAR");
        response.put("match", hasMatches);
        response.put("matchFound", hasMatches);
        response.put("matchCount", hasMatches ? resp.matches().size() : 0);
        response.put("highestMatchScore", topScore);
        response.put("confidence", topScore); // already 0..1 from microservice
        response.put("entityType", type != null ? type : "PERSON");
        response.put("screeningProvider", "AML_MICROSERVICE");
        response.put("screenedAt", resp.checkedAt());

        List<Map<String, Object>> matchesList = new ArrayList<>();
        List<Map<String, Object>> hitsList = new ArrayList<>();
        if (hasMatches) {
            for (BackendSanctionsScreenResponse.MatchDto m : resp.matches()) {
                Map<String, Object> mm = new HashMap<>();
                mm.put("name", m.matchedName());
                mm.put("matchedName", m.matchedName());
                mm.put("list", m.listName());
                mm.put("sourceList", m.listName());
                mm.put("listName", m.listName());
                mm.put("similarityScore", m.similarityScore());
                mm.put("score", m.similarityScore());
                mm.put("entityId", m.entityId());
                mm.put("reason", "Sanctions match");
                matchesList.add(mm);

                Map<String, Object> hh = new HashMap<>();
                hh.put("matchedName", m.matchedName());
                hh.put("sourceList", m.listName());
                hh.put("entityId", m.entityId());
                hh.put("reason", "Sanctions match");
                hitsList.add(hh);
            }
        }
        response.put("matches", matchesList);
        response.put("hits", hitsList);
        return response;
    }

    /**
     * Screen a person (with DOB).
     * POST /api/v1/sanctions/screen/person
     */
    @PostMapping("/screen/person")
    public ResponseEntity<ScreeningResult> screenPerson(@RequestBody PersonScreeningRequest request) {
        log.info("Screening person: {}", request.getFullName());
        try {
            ScreeningResult result = screeningService.screenBeneficialOwner(
                    request.getFullName(), request.getDateOfBirth());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error screening person: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Screen an organization.
     * POST /api/v1/sanctions/screen/organization
     */
    @PostMapping("/screen/organization")
    public ResponseEntity<ScreeningResult> screenOrganization(@RequestBody OrganizationScreeningRequest request) {
        log.info("Screening organization: {}", request.getLegalName());
        try {
            ScreeningResult result = screeningService.screenMerchant(
                    request.getLegalName(), request.getTradingName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error screening organization: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Health check. */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Sanctions screening service is healthy");
    }

    // DTOs for JSON deserialization - constructors and setters are used by Jackson
    @SuppressWarnings("unused")
    private static class ScreeningRequest {
        private String name;
        private String entityType;
        public ScreeningRequest() {}
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
    }

    @SuppressWarnings("unused")
    private static class PersonScreeningRequest {
        private String fullName;
        private LocalDate dateOfBirth;
        public PersonScreeningRequest() {}
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    }

    @SuppressWarnings("unused")
    private static class OrganizationScreeningRequest {
        private String legalName;
        private String tradingName;
        public OrganizationScreeningRequest() {}
        public String getLegalName() { return legalName; }
        public void setLegalName(String legalName) { this.legalName = legalName; }
        public String getTradingName() { return tradingName; }
        public void setTradingName(String tradingName) { this.tradingName = tradingName; }
    }
}
