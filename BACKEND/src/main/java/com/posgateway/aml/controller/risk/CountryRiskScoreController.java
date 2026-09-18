package com.posgateway.aml.controller.risk;

import com.posgateway.aml.entity.risk.CountryRiskScore;
import com.posgateway.aml.repository.risk.CountryRiskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Country Risk Score Management — fully DB-backed.
 *
 * <p>Provides REST CRUD for the {@code country_risk_scores} table so that:
 * <ul>
 *   <li>Frontend admin pages can list all countries with their numeric risk
 *       scores for dashboards and dropdowns</li>
 *   <li>Compliance officers can update the FATF-level risk tier per country
 *       (BLACKLIST, GREYLIST, ENHANCED_DD)</li>
 *   <li>{@code RiskScoringService} reads from this table for KRS/TRS/CRA
 *       calculation</li>
 * </ul>
 *
 * <p>ADMIN and COMPLIANCE_OFFICER roles can manage; all authenticated users
 * can read.
 */
@RestController
@RequestMapping("/risk/country-scores")
@PreAuthorize("isAuthenticated()")
public class CountryRiskScoreController {

    private static final Logger log = LoggerFactory.getLogger(CountryRiskScoreController.class);

    private final CountryRiskRepository repository;

    public CountryRiskScoreController(CountryRiskRepository repository) {
        this.repository = repository;
    }

    // ──────────────────────────────────────────────────────────────────
    // GET — list all (for dashboards, dropdowns)
    // ──────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<CountryRiskScore>> listAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /{countryCode} — single score
    // ──────────────────────────────────────────────────────────────────

    @GetMapping("/{countryCode}")
    public ResponseEntity<CountryRiskScore> getByCode(@PathVariable String countryCode) {
        return repository.findByCountryCode(normalise(countryCode))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ──────────────────────────────────────────────────────────────────
    // POST — create or fully replace a score entry
    // ──────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COMPLIANCE_OFFICER')")
    public ResponseEntity<CountryRiskScore> create(@RequestBody CountryRiskScore score) {
        String code = normalise(score.getCountryCode());
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        score.setCountryCode(code);

        if (repository.findByCountryCode(code).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        if (score.getRiskScore() == null) score.setRiskScore(50);
        if (score.getRiskTier() == null) score.setRiskTier("MEDIUM");
        if (score.getLastReviewedAt() == null) score.setLastReviewedAt(OffsetDateTime.now());
        if (score.getUpdatedAt() == null) score.setUpdatedAt(OffsetDateTime.now());
        if (score.getSource() == null) score.setSource("MANUAL");

        CountryRiskScore saved = repository.save(score);
        log.info("Created country risk score: {} ({}) — score={}, tier={}",
                saved.getCountryCode(), saved.getCountryName(), saved.getRiskScore(), saved.getRiskTier());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ──────────────────────────────────────────────────────────────────
    // PUT /{countryCode} — update existing
    // ──────────────────────────────────────────────────────────────────

    @PutMapping("/{countryCode}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COMPLIANCE_OFFICER')")
    public ResponseEntity<CountryRiskScore> update(@PathVariable String countryCode,
                                                    @RequestBody CountryRiskScore updates) {
        String code = normalise(countryCode);
        Optional<CountryRiskScore> opt = repository.findByCountryCode(code);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CountryRiskScore entity = opt.get();
        if (updates.getCountryName() != null) entity.setCountryName(updates.getCountryName());
        if (updates.getRiskScore() != null) entity.setRiskScore(updates.getRiskScore());
        if (updates.getRiskTier() != null) entity.setRiskTier(updates.getRiskTier());
        if (updates.getFatfListed() != null) entity.setFatfListed(updates.getFatfListed());
        if (updates.getFatfStatus() != null) entity.setFatfStatus(updates.getFatfStatus());
        if (updates.getSource() != null) entity.setSource(updates.getSource());
        entity.setUpdatedAt(OffsetDateTime.now());

        CountryRiskScore saved = repository.save(entity);
        log.info("Updated country risk score: {} — score={}, tier={}",
                saved.getCountryCode(), saved.getRiskScore(), saved.getRiskTier());
        return ResponseEntity.ok(saved);
    }

    // ──────────────────────────────────────────────────────────────────
    // DELETE /{countryCode} — remove a score entry
    // ──────────────────────────────────────────────────────────────────

    @DeleteMapping("/{countryCode}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COMPLIANCE_OFFICER')")
    public ResponseEntity<Void> delete(@PathVariable String countryCode) {
        String code = normalise(countryCode);
        Optional<CountryRiskScore> opt = repository.findByCountryCode(code);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        repository.delete(opt.get());
        log.info("Deleted country risk score: {}", code);
        return ResponseEntity.noContent().build();
    }

    private static String normalise(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}