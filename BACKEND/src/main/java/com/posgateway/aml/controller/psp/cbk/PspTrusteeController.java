package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspTrusteeDto;
import com.posgateway.aml.entity.psp.cbk.PspTrustee;
import com.posgateway.aml.repository.psp.cbk.PspTrusteeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * CBK GDI – Schedule of Trustees (annual, Jan 5).
 * Endpoint: /api/v1/psps/{pspId}/cbk/trustees
 */
@RestController
@RequestMapping("/api/v1/psps/{pspId}/cbk/trustees")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspTrusteeController {

    private static final Logger log = LoggerFactory.getLogger(PspTrusteeController.class);

    private final PspTrusteeRepository repository;

    public PspTrusteeController(PspTrusteeRepository repository) {
        this.repository = repository;
    }

    private com.posgateway.aml.entity.User getCurrentUser() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.posgateway.aml.entity.User) {
            return (com.posgateway.aml.entity.User) auth.getPrincipal();
        }
        return null;
    }

    private boolean canAccess(com.posgateway.aml.entity.User user, Long pspId) {
        if (user == null) return false;
        com.posgateway.aml.model.UserRole role =
                com.posgateway.aml.model.UserRole.valueOf(user.getRole().getName());
        if (role == com.posgateway.aml.model.UserRole.PSP_ADMIN) {
            return user.getPsp() != null && user.getPsp().getPspId().equals(pspId);
        }
        return true;
    }

    @GetMapping
    public ResponseEntity<List<PspTrustee>> list(@PathVariable Long pspId) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspTrustee> getById(@PathVariable Long pspId, @PathVariable Long id) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspTrustee> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspTrustee> create(@PathVariable Long pspId,
                                             @RequestBody PspTrusteeDto dto) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        PspTrustee e = PspTrustee.builder()
                .pspId(pspId)
                .trustCompName(dto.getTrustCompName())
                .directorsTrustComp(dto.getDirectorsTrustComp())
                .trusteeNames(dto.getTrusteeNames())
                .trusteeGender(dto.getTrusteeGender())
                .dob(dto.getDob())
                .nationality(dto.getNationality())
                .residentCountry(dto.getResidentCountry())
                .idNoPassport(dto.getIdNoPassport())
                .pin(dto.getPin())
                .contactNumber(dto.getContactNumber())
                .qualifications(dto.getQualifications())
                .othersTrusteeships(dto.getOthersTrusteeships())
                .disclosures(dto.getDisclosures())
                .shareholders(dto.getShareholders())
                .shareholdingPercentage(dto.getShareholdingPercentage())
                .build();
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspTrustee> update(@PathVariable Long pspId,
                                             @PathVariable Long id,
                                             @RequestBody PspTrusteeDto dto) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspTrustee> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspTrustee e = opt.get();
        e.setTrustCompName(dto.getTrustCompName());
        e.setDirectorsTrustComp(dto.getDirectorsTrustComp());
        e.setTrusteeNames(dto.getTrusteeNames());
        e.setTrusteeGender(dto.getTrusteeGender());
        e.setDob(dto.getDob());
        e.setNationality(dto.getNationality());
        e.setResidentCountry(dto.getResidentCountry());
        e.setIdNoPassport(dto.getIdNoPassport());
        e.setPin(dto.getPin());
        e.setContactNumber(dto.getContactNumber());
        e.setQualifications(dto.getQualifications());
        e.setOthersTrusteeships(dto.getOthersTrusteeships());
        e.setDisclosures(dto.getDisclosures());
        e.setShareholders(dto.getShareholders());
        e.setShareholdingPercentage(dto.getShareholdingPercentage());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspTrustee> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
