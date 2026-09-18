package com.posgateway.aml.controller.risk;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.risk.HighRiskCountry;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * High-Risk Country Management — fully DB-backed.
 *
 * <p>Provides REST CRUD for the {@code high_risk_countries} table so that:
 * <ul>
 *   <li>Frontend admin pages can list countries for dropdowns / filters</li>
 *   <li>Compliance officers can add/remove/edit countries at runtime</li>
 *   <li>The risk-scoring services ({@code FraudDetectionService},
 *       {@code AmlService}, {@code RiskScoringService}) read from this table
 *       as their primary data source</li>
 * </ul>
 *
 * <p>ADMIN and COMPLIANCE_OFFICER roles can manage; all authenticated users
 * can read the list.
 */
@RestController
@RequestMapping("/risk/high-risk-countries")
@PreAuthorize("isAuthenticated()")
public class HighRiskCountryController {

    private static final Logger log = LoggerFactory.getLogger(HighRiskCountryController.class);

    private final HighRiskCountryRepository repository;

    public HighRiskCountryController(HighRiskCountryRepository repository) {
        this.repository = repository;
    }

    // ──────────────────────────────────────────────────────────────────
    // GET — list all (for dropdowns, filters, compliance dashboard)
    // ──────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<HighRiskCountry>> listAll() {
        List<HighRiskCountry> list = repository.findAll();
        return ResponseEntity.ok(list);
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /{id} — single country detail
    // ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<HighRiskCountry> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ──────────────────────────────────────────────────────────────────
    // POST — create a new high-risk country entry
    // ──────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COMPLIANCE_OFFICER')")
    public ResponseEntity<HighRiskCountry> create(@RequestBody HighRiskCountry country) {
        if (country.getCountryCode() == null || country.getCountryCode().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Normalise country code to uppercase
        country.setCountryCode(country.getCountryCode().trim().toUpperCase());

        // Check for duplicate
        Optional<HighRiskCountry> existing = repository.findByCountryCode(country.getCountryCode());
        if (existing.isPresent()) {
            log.warn("Duplicate high-risk country entry rejected: {}", country.getCountryCode());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        // Set defaults
        if (country.getRiskLevel() == null || country.getRiskLevel().isBlank()) {
            country.setRiskLevel("HIGH");
        }
        country.setAddedAt(LocalDateTime.now());
        country.setAddedBy(getCurrentUsername());

        HighRiskCountry saved = repository.save(country);
        log.info("Created high-risk country: {} ({}) — level={}, by={}",
                saved.getCountryCode(), saved.getCountryName(), saved.getRiskLevel(), saved.getAddedBy());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ──────────────────────────────────────────────────────────────────
    // PUT /{id} — update risk level and name
    // ──────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COMPLIANCE_OFFICER')")
    public ResponseEntity<HighRiskCountry> update(@PathVariable Long id,
                                                   @RequestBody HighRiskCountry updates) {
        Optional<HighRiskCountry> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        HighRiskCountry entity = opt.get();
        if (updates.getCountryName() != null) {
            entity.setCountryName(updates.getCountryName());
        }
        if (updates.getRiskLevel() != null) {
            entity.setRiskLevel(updates.getRiskLevel());
        }
        // countryCode is immutable after creation

        HighRiskCountry saved = repository.save(entity);
        log.info("Updated high-risk country: {} ({}) — level={}",
                saved.getCountryCode(), saved.getCountryName(), saved.getRiskLevel());
        return ResponseEntity.ok(saved);
    }

    // ──────────────────────────────────────────────────────────────────
    // DELETE /{id} — remove a country from the high-risk list
    // ──────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COMPLIANCE_OFFICER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Optional<HighRiskCountry> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        log.info("Removed high-risk country: {} ({})", opt.get().getCountryCode(), opt.get().getCountryName());
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "SYSTEM";
        return auth.getName();
    }
}