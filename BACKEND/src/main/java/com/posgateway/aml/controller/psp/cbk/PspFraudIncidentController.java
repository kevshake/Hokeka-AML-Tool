package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspFraudIncidentDto;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.cbk.PspFraudIncident;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.psp.cbk.PspFraudIncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * CBK GDI #7 – Fraud / Theft / Robbery Incidents (daily).
 * Endpoint: /api/v1/psps/{pspId}/cbk/fraud-incidents
 */
@RestController
@RequestMapping("/api/v1/psps/{pspId}/cbk/fraud-incidents")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspFraudIncidentController {

    private static final Logger log = LoggerFactory.getLogger(PspFraudIncidentController.class);

    private final PspFraudIncidentRepository repository;

    public PspFraudIncidentController(PspFraudIncidentRepository repository) {
        this.repository = repository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return (principal instanceof User user) ? user : null;
    }

    private Long getCurrentPspId() {
        User u = getCurrentUser();
        return (u != null && u.getPsp() != null) ? u.getPsp().getPspId() : null;
    }

    private boolean canAccess(User user, Long pspId) {
        if (user == null || user.getRole() == null || user.getRole().getName() == null) return false;
        UserRole role;
        try {
            role = UserRole.valueOf(user.getRole().getName());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (role == UserRole.PSP_ADMIN) {
            return user.getPsp() != null && pspId != null && pspId.equals(user.getPsp().getPspId());
        }
        return true;
    }

    @GetMapping
    public ResponseEntity<List<PspFraudIncident>> list(@PathVariable Long pspId) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspFraudIncident> getById(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspFraudIncident> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspFraudIncident> create(@PathVariable Long pspId,
                                                   @RequestBody PspFraudIncidentDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        PspFraudIncident e = PspFraudIncident.builder()
                .pspId(pspId)
                .reportingDate(dto.getReportingDate())
                .subCountyCode(dto.getSubCountyCode())
                .subFraudCode(dto.getSubFraudCode())
                .fraudCategoryFlag(dto.getFraudCategoryFlag())
                .victimCategory(dto.getVictimCategory())
                .victimInformation(dto.getVictimInformation())
                .dateOfOccurrence(dto.getDateOfOccurrence())
                .numberOfIncidences(dto.getNumberOfIncidences())
                .amountInvolved(dto.getAmountInvolved())
                .amountLost(dto.getAmountLost())
                .amountRecovered(dto.getAmountRecovered())
                .actionTaken(dto.getActionTaken())
                .recoveryDetails(dto.getRecoveryDetails())
                .alertIdLink(dto.getAlertIdLink())
                .caseIdLink(dto.getCaseIdLink())
                .build();
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspFraudIncident> update(@PathVariable Long pspId,
                                                   @PathVariable Long id,
                                                   @RequestBody PspFraudIncidentDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspFraudIncident> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspFraudIncident e = opt.get();
        e.setReportingDate(dto.getReportingDate());
        e.setSubCountyCode(dto.getSubCountyCode());
        e.setSubFraudCode(dto.getSubFraudCode());
        e.setFraudCategoryFlag(dto.getFraudCategoryFlag());
        e.setVictimCategory(dto.getVictimCategory());
        e.setVictimInformation(dto.getVictimInformation());
        e.setDateOfOccurrence(dto.getDateOfOccurrence());
        e.setNumberOfIncidences(dto.getNumberOfIncidences());
        e.setAmountInvolved(dto.getAmountInvolved());
        e.setAmountLost(dto.getAmountLost());
        e.setAmountRecovered(dto.getAmountRecovered());
        e.setActionTaken(dto.getActionTaken());
        e.setRecoveryDetails(dto.getRecoveryDetails());
        e.setAlertIdLink(dto.getAlertIdLink());
        e.setCaseIdLink(dto.getCaseIdLink());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspFraudIncident> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
