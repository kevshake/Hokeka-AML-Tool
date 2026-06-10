package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspSystemInterruptionDto;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.cbk.PspSystemInterruption;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.psp.cbk.PspSystemInterruptionRepository;
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
 * CBK GDI #8 – System Stability / Service Interruptions (daily).
 * Endpoint: /api/v1/psps/{pspId}/cbk/system-interruptions
 */
@RestController
@RequestMapping("/psps/{pspId}/cbk/system-interruptions")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspSystemInterruptionController {

    private static final Logger log = LoggerFactory.getLogger(PspSystemInterruptionController.class);

    private final PspSystemInterruptionRepository repository;

    public PspSystemInterruptionController(PspSystemInterruptionRepository repository) {
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
    public ResponseEntity<List<PspSystemInterruption>> list(@PathVariable Long pspId) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspSystemInterruption> getById(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspSystemInterruption> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspSystemInterruption> create(@PathVariable Long pspId,
                                                        @RequestBody PspSystemInterruptionDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        PspSystemInterruption e = PspSystemInterruption.builder()
                .pspId(pspId)
                .reportingDate(dto.getReportingDate())
                .subCountyCode(dto.getSubCountyCode())
                .systemOwnerFlag(dto.getSystemOwnerFlag())
                .thirdPartyOwnedCategory(dto.getThirdPartyOwnedCategory())
                .thirdPartyName(dto.getThirdPartyName())
                .productType(dto.getProductType())
                .systemUnavailabilityTypeCode(dto.getSystemUnavailabilityTypeCode())
                .thirdPartySystemAffected(dto.getThirdPartySystemAffected())
                .serviceInterruptionCauseCode(dto.getServiceInterruptionCauseCode())
                .severityInterruptionCode(dto.getSeverityInterruptionCode())
                .recoveryTimeCode(dto.getRecoveryTimeCode())
                .remedialStatusCode(dto.getRemedialStatusCode())
                .systemUptimePercentage(dto.getSystemUptimePercentage())
                .startedAt(dto.getStartedAt())
                .resolvedAt(dto.getResolvedAt())
                .build();
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspSystemInterruption> update(@PathVariable Long pspId,
                                                        @PathVariable Long id,
                                                        @RequestBody PspSystemInterruptionDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspSystemInterruption> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspSystemInterruption e = opt.get();
        e.setReportingDate(dto.getReportingDate());
        e.setSubCountyCode(dto.getSubCountyCode());
        e.setSystemOwnerFlag(dto.getSystemOwnerFlag());
        e.setThirdPartyOwnedCategory(dto.getThirdPartyOwnedCategory());
        e.setThirdPartyName(dto.getThirdPartyName());
        e.setProductType(dto.getProductType());
        e.setSystemUnavailabilityTypeCode(dto.getSystemUnavailabilityTypeCode());
        e.setThirdPartySystemAffected(dto.getThirdPartySystemAffected());
        e.setServiceInterruptionCauseCode(dto.getServiceInterruptionCauseCode());
        e.setSeverityInterruptionCode(dto.getSeverityInterruptionCode());
        e.setRecoveryTimeCode(dto.getRecoveryTimeCode());
        e.setRemedialStatusCode(dto.getRemedialStatusCode());
        e.setSystemUptimePercentage(dto.getSystemUptimePercentage());
        e.setStartedAt(dto.getStartedAt());
        e.setResolvedAt(dto.getResolvedAt());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspSystemInterruption> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
