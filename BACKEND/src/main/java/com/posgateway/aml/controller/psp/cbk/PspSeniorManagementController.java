package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspSeniorManagementDto;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.cbk.PspSeniorManagement;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.psp.cbk.PspSeniorManagementRepository;
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
 * CBK GDI – Senior Management Schedule (annual, Jan 5).
 * Endpoint: /api/v1/psps/{pspId}/cbk/senior-management
 */
@RestController
@RequestMapping("/psps/{pspId}/cbk/senior-management")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspSeniorManagementController {

    private static final Logger log = LoggerFactory.getLogger(PspSeniorManagementController.class);

    private final PspSeniorManagementRepository repository;

    public PspSeniorManagementController(PspSeniorManagementRepository repository) {
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
    public ResponseEntity<List<PspSeniorManagement>> list(@PathVariable Long pspId) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspSeniorManagement> getById(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspSeniorManagement> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspSeniorManagement> create(@PathVariable Long pspId,
                                                      @RequestBody PspSeniorManagementDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        PspSeniorManagement e = PspSeniorManagement.builder()
                .pspId(pspId)
                .officerNames(dto.getOfficerNames())
                .gender(dto.getGender())
                .designation(dto.getDesignation())
                .dob(dto.getDob())
                .nationality(dto.getNationality())
                .idNo(dto.getIdNo())
                .taxId(dto.getTaxId())
                .qualification(dto.getQualification())
                .dateOfEmp(dto.getDateOfEmp())
                .empType(dto.getEmpType())
                .retirementDt(dto.getRetirementDt())
                .externalAffliates(dto.getExternalAffliates())
                .otherDisclosure(dto.getOtherDisclosure())
                .build();
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspSeniorManagement> update(@PathVariable Long pspId,
                                                      @PathVariable Long id,
                                                      @RequestBody PspSeniorManagementDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspSeniorManagement> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspSeniorManagement e = opt.get();
        e.setOfficerNames(dto.getOfficerNames());
        e.setGender(dto.getGender());
        e.setDesignation(dto.getDesignation());
        e.setDob(dto.getDob());
        e.setNationality(dto.getNationality());
        e.setIdNo(dto.getIdNo());
        e.setTaxId(dto.getTaxId());
        e.setQualification(dto.getQualification());
        e.setDateOfEmp(dto.getDateOfEmp());
        e.setEmpType(dto.getEmpType());
        e.setRetirementDt(dto.getRetirementDt());
        e.setExternalAffliates(dto.getExternalAffliates());
        e.setOtherDisclosure(dto.getOtherDisclosure());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspSeniorManagement> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
