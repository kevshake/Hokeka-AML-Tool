package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspShareholderDto;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.cbk.PspShareholder;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.psp.cbk.PspShareholderRepository;
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
 * CBK GDI – Schedule of Shareholders (annual, Jan 4).
 * Endpoint: /api/v1/psps/{pspId}/cbk/shareholders
 */
@RestController
@RequestMapping("/psps/{pspId}/cbk/shareholders")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspShareholderController {

    private static final Logger log = LoggerFactory.getLogger(PspShareholderController.class);

    private final PspShareholderRepository repository;

    public PspShareholderController(PspShareholderRepository repository) {
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
    public ResponseEntity<List<PspShareholder>> list(@PathVariable Long pspId) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspShareholder> getById(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspShareholder> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspShareholder> create(@PathVariable Long pspId,
                                                 @RequestBody PspShareholderDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        PspShareholder e = PspShareholder.builder()
                .pspId(pspId)
                .shareholderName(dto.getShareholderName())
                .shareholderGender(dto.getShareholderGender())
                .shareholderType(dto.getShareholderType())
                .dobOrRegDate(dto.getDobOrRegDate())
                .nationality(dto.getNationality())
                .residentCountry(dto.getResidentCountry())
                .countryOfInc(dto.getCountryOfInc())
                .idNoPassport(dto.getIdNoPassport())
                .pin(dto.getPin())
                .contactNumber(dto.getContactNumber())
                .qualifications(dto.getQualifications())
                .previousEmployment(dto.getPreviousEmployment())
                .onboardingDate(dto.getOnboardingDate())
                .noOfSharesHeld(dto.getNoOfSharesHeld())
                .shareValue(dto.getShareValue())
                .percentageOfShare(dto.getPercentageOfShare())
                .build();
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspShareholder> update(@PathVariable Long pspId,
                                                 @PathVariable Long id,
                                                 @RequestBody PspShareholderDto dto) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspShareholder> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspShareholder e = opt.get();
        e.setShareholderName(dto.getShareholderName());
        e.setShareholderGender(dto.getShareholderGender());
        e.setShareholderType(dto.getShareholderType());
        e.setDobOrRegDate(dto.getDobOrRegDate());
        e.setNationality(dto.getNationality());
        e.setResidentCountry(dto.getResidentCountry());
        e.setCountryOfInc(dto.getCountryOfInc());
        e.setIdNoPassport(dto.getIdNoPassport());
        e.setPin(dto.getPin());
        e.setContactNumber(dto.getContactNumber());
        e.setQualifications(dto.getQualifications());
        e.setPreviousEmployment(dto.getPreviousEmployment());
        e.setOnboardingDate(dto.getOnboardingDate());
        e.setNoOfSharesHeld(dto.getNoOfSharesHeld());
        e.setShareValue(dto.getShareValue());
        e.setPercentageOfShare(dto.getPercentageOfShare());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspShareholder> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
