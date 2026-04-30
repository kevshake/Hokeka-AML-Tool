package com.posgateway.aml.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.service.aml.AerospikeSanctionsScreeningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for direct sanctions screening
 */
// @Slf4j removed
@RestController
@RequestMapping("/sanctions")
public class SanctionsScreeningController {

    private static final Logger log = LoggerFactory.getLogger(SanctionsScreeningController.class);

    @Autowired
    private AerospikeSanctionsScreeningService screeningService;

    /**
     * Screen a name against sanctions database
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

            // Convert entity type string to enum, defaulting to PERSON
            ScreeningResult.EntityType entityType = ScreeningResult.EntityType.PERSON;
            if (request.getEntityType() != null && !request.getEntityType().trim().isEmpty()) {
                try {
                    entityType = ScreeningResult.EntityType.valueOf(request.getEntityType().toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid entity type '{}', defaulting to PERSON", request.getEntityType());
                    entityType = ScreeningResult.EntityType.PERSON;
                }
            }

            ScreeningResult result = screeningService.screenName(request.getName().trim(), entityType);

            // Convert ScreeningResult to frontend-expected format
            Map<String, Object> response = new HashMap<>();
            response.put("match", result.hasMatches());
            response.put("status", result.getStatus() != null ? result.getStatus().name() : "CLEAR");
            response.put("matchCount", result.getMatchCount() != null ? result.getMatchCount() : 0);
            response.put("highestMatchScore", result.getHighestMatchScore());
            response.put("confidence", result.getHighestMatchScore() != null ? result.getHighestMatchScore() / 100.0 : 0.0);
            response.put("screenedName", result.getScreenedName());
            response.put("entityType", result.getEntityType() != null ? result.getEntityType().name() : "PERSON");
            response.put("screeningProvider", result.getScreeningProvider());
            response.put("screenedAt", result.getScreenedAt());

            // Convert matches to frontend format
            if (result.getMatches() != null && !result.getMatches().isEmpty()) {
                java.util.List<Map<String, Object>> matchesList = new java.util.ArrayList<>();
                java.util.List<Map<String, Object>> hitsList = new java.util.ArrayList<>();
                
                for (ScreeningResult.Match match : result.getMatches()) {
                    Map<String, Object> matchMap = new HashMap<>();
                    matchMap.put("name", match.getMatchedName());
                    matchMap.put("matchedName", match.getMatchedName());
                    matchMap.put("list", match.getListName());
                    matchMap.put("sourceList", match.getListName());
                    matchMap.put("listName", match.getListName());
                    matchMap.put("similarityScore", match.getSimilarityScore());
                    matchMap.put("reason", match.getSanctionType() != null ? match.getSanctionType() : "Sanctions match");
                    matchMap.put("entityType", match.getEntityType() != null ? match.getEntityType().name() : "PERSON");
                    matchesList.add(matchMap);
                    
                    Map<String, Object> hitMap = new HashMap<>();
                    hitMap.put("matchedName", match.getMatchedName());
                    hitMap.put("sourceList", match.getListName());
                    hitMap.put("reason", match.getSanctionType() != null ? match.getSanctionType() : "Sanctions match");
                    hitsList.add(hitMap);
                }
                
                response.put("matches", matchesList);
                response.put("hits", hitsList);
            } else {
                response.put("matches", new java.util.ArrayList<>());
                response.put("hits", new java.util.ArrayList<>());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error screening name '{}': {}", request.getName(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error performing screening: " + e.getMessage());
            error.put("message", "Error performing screening. Please try again.");
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Screen a person (with DOB)
     * POST /api/v1/sanctions/screen/person
     */
    @PostMapping("/screen/person")
    public ResponseEntity<ScreeningResult> screenPerson(@RequestBody PersonScreeningRequest request) {
        log.info("Screening person: {}", request.getFullName());

        try {
            ScreeningResult result = screeningService.screenBeneficialOwner(
                    request.getFullName(),
                    request.getDateOfBirth());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error screening person: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Screen an organization
     * POST /api/v1/sanctions/screen/organization
     */
    @PostMapping("/screen/organization")
    public ResponseEntity<ScreeningResult> screenOrganization(@RequestBody OrganizationScreeningRequest request) {
        log.info("Screening organization: {}", request.getLegalName());

        try {
            ScreeningResult result = screeningService.screenMerchant(
                    request.getLegalName(),
                    request.getTradingName());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error screening organization: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Sanctions screening service is healthy");
    }

    // DTOs for JSON deserialization - constructors and setters are used by Jackson
    @SuppressWarnings("unused")
    private static class ScreeningRequest {
        private String name;
        private String entityType; // PERSON, ORGANIZATION, VESSEL

        public ScreeningRequest() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEntityType() {
            return entityType;
        }

        public void setEntityType(String entityType) {
            this.entityType = entityType;
        }
    }

    @SuppressWarnings("unused")
    private static class PersonScreeningRequest {
        private String fullName;
        private LocalDate dateOfBirth;

        public PersonScreeningRequest() {
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public LocalDate getDateOfBirth() {
            return dateOfBirth;
        }

        public void setDateOfBirth(LocalDate dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }
    }

    @SuppressWarnings("unused")
    private static class OrganizationScreeningRequest {
        private String legalName;
        private String tradingName;

        public OrganizationScreeningRequest() {
        }

        public String getLegalName() {
            return legalName;
        }

        public void setLegalName(String legalName) {
            this.legalName = legalName;
        }

        public String getTradingName() {
            return tradingName;
        }

        public void setTradingName(String tradingName) {
            this.tradingName = tradingName;
        }
    }
}
