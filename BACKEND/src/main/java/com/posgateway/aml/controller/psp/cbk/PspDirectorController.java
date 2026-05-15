package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspDirectorDto;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.cbk.PspDirector;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.psp.cbk.PspDirectorRepository;
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
 * CBK GDI – Schedule of Directors (annual, Jan 5).
 * Endpoint: /api/v1/psps/{pspId}/cbk/directors
 */
@RestController
@RequestMapping("/api/v1/psps/{pspId}/cbk/directors")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspDirectorController {

    private static final Logger log = LoggerFactory.getLogger(PspDirectorController.class);

    private final PspDirectorRepository repository;

    public PspDirectorController(PspDirectorRepository repository) {
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

    private boolean isPspAdminForThisPsp(User user, Long pspId) {
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
        return true; // ADMIN / COMPLIANCE_OFFICER see all
    }

    @GetMapping
    public ResponseEntity<List<PspDirector>> list(@PathVariable Long pspId) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!isPspAdminForThisPsp(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspDirector> getById(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!isPspAdminForThisPsp(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspDirector> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspDirector> create(@PathVariable Long pspId,
                                              @RequestBody PspDirectorDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!isPspAdminForThisPsp(user, pspId)) return ResponseEntity.status(403).build();

        PspDirector entity = PspDirector.builder()
                .pspId(pspId)
                .directorNames(dto.getDirectorNames())
                .directorGender(dto.getDirectorGender())
                .typeOfDirector(dto.getTypeOfDirector())
                .dob(dto.getDob())
                .nationality(dto.getNationality())
                .residentCountry(dto.getResidentCountry())
                .idNoPassport(dto.getIdNoPassport())
                .pin(dto.getPin())
                .contactNumber(dto.getContactNumber())
                .qualifications(dto.getQualifications())
                .otherDirectorships(dto.getOtherDirectorships())
                .dateOfAppointment(dto.getDateOfAppointment())
                .dateOfRetirement(dto.getDateOfRetirement())
                .retirementReason(dto.getRetirementReason())
                .disclosures(dto.getDisclosures())
                .build();
        return ResponseEntity.ok(repository.save(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspDirector> update(@PathVariable Long pspId,
                                              @PathVariable Long id,
                                              @RequestBody PspDirectorDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!isPspAdminForThisPsp(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspDirector> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspDirector e = opt.get();
        e.setDirectorNames(dto.getDirectorNames());
        e.setDirectorGender(dto.getDirectorGender());
        e.setTypeOfDirector(dto.getTypeOfDirector());
        e.setDob(dto.getDob());
        e.setNationality(dto.getNationality());
        e.setResidentCountry(dto.getResidentCountry());
        e.setIdNoPassport(dto.getIdNoPassport());
        e.setPin(dto.getPin());
        e.setContactNumber(dto.getContactNumber());
        e.setQualifications(dto.getQualifications());
        e.setOtherDirectorships(dto.getOtherDirectorships());
        e.setDateOfAppointment(dto.getDateOfAppointment());
        e.setDateOfRetirement(dto.getDateOfRetirement());
        e.setRetirementReason(dto.getRetirementReason());
        e.setDisclosures(dto.getDisclosures());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!isPspAdminForThisPsp(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspDirector> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
