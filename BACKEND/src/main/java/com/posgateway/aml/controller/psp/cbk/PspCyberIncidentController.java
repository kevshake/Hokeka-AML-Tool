package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspCyberIncidentDto;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.cbk.PspCyberIncident;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.psp.cbk.PspCyberIncidentRepository;
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
 * CBK GDI #6 – Cybersecurity Incident Records (daily).
 * Endpoint: /api/v1/psps/{pspId}/cbk/cyber-incidents
 */
@RestController
@RequestMapping("/psps/{pspId}/cbk/cyber-incidents")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspCyberIncidentController {

    private static final Logger log = LoggerFactory.getLogger(PspCyberIncidentController.class);

    private final PspCyberIncidentRepository repository;

    public PspCyberIncidentController(PspCyberIncidentRepository repository) {
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
    public ResponseEntity<List<PspCyberIncident>> list(@PathVariable Long pspId) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspCyberIncident> getById(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspCyberIncident> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspCyberIncident> create(@PathVariable Long pspId,
                                                   @RequestBody PspCyberIncidentDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        PspCyberIncident e = PspCyberIncident.builder()
                .pspId(pspId)
                .incidentNumber(dto.getIncidentNumber())
                .incidentDate(dto.getIncidentDate())
                .locationOfAttacker(dto.getLocationOfAttacker())
                .incidentMode(dto.getIncidentMode())
                .lossType(dto.getLossType())
                .details(dto.getDetails())
                .actionTaken(dto.getActionTaken())
                .resolutionDate(dto.getResolutionDate())
                .mitigationActions(dto.getMitigationActions())
                .amountInvolved(dto.getAmountInvolved())
                .amountLost(dto.getAmountLost())
                .currency(dto.getCurrency())
                .createdBy(dto.getCreatedBy())
                .build();
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspCyberIncident> update(@PathVariable Long pspId,
                                                   @PathVariable Long id,
                                                   @RequestBody PspCyberIncidentDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspCyberIncident> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspCyberIncident e = opt.get();
        e.setIncidentNumber(dto.getIncidentNumber());
        e.setIncidentDate(dto.getIncidentDate());
        e.setLocationOfAttacker(dto.getLocationOfAttacker());
        e.setIncidentMode(dto.getIncidentMode());
        e.setLossType(dto.getLossType());
        e.setDetails(dto.getDetails());
        e.setActionTaken(dto.getActionTaken());
        e.setResolutionDate(dto.getResolutionDate());
        e.setMitigationActions(dto.getMitigationActions());
        e.setAmountInvolved(dto.getAmountInvolved());
        e.setAmountLost(dto.getAmountLost());
        e.setCurrency(dto.getCurrency());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspCyberIncident> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
