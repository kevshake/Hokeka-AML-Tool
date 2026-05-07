package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspTrustAccountDto;
import com.posgateway.aml.entity.psp.cbk.PspTrustAccount;
import com.posgateway.aml.repository.psp.cbk.PspTrustAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * CBK GDI – Trust Account balances (daily).
 * Endpoint: /api/v1/psps/{pspId}/cbk/trust-accounts
 */
@RestController
@RequestMapping("/api/v1/psps/{pspId}/cbk/trust-accounts")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspTrustAccountController {

    private static final Logger log = LoggerFactory.getLogger(PspTrustAccountController.class);

    private final PspTrustAccountRepository repository;

    public PspTrustAccountController(PspTrustAccountRepository repository) {
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
    public ResponseEntity<List<PspTrustAccount>> list(@PathVariable Long pspId) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspTrustAccount> getById(@PathVariable Long pspId, @PathVariable Long id) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspTrustAccount> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspTrustAccount> create(@PathVariable Long pspId,
                                                  @RequestBody PspTrustAccountDto dto) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        PspTrustAccount e = PspTrustAccount.builder()
                .pspId(pspId)
                .bankId(dto.getBankId())
                .bankAccountNumber(dto.getBankAccountNumber())
                .trustAccDrTypeCode(dto.getTrustAccDrTypeCode())
                .orgReceivingDonation(dto.getOrgReceivingDonation())
                .sectorCode(dto.getSectorCode())
                .trustAccIntUtilizedDetails(dto.getTrustAccIntUtilizedDetails())
                .openingBalance(dto.getOpeningBalance())
                .principalAmount(dto.getPrincipalAmount())
                .interestEarned(dto.getInterestEarned())
                .closingBalance(dto.getClosingBalance())
                .interestUtilized(dto.getInterestUtilized())
                .trustFields(dto.getTrustFields())
                .asOfDate(dto.getAsOfDate())
                .build();
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspTrustAccount> update(@PathVariable Long pspId,
                                                  @PathVariable Long id,
                                                  @RequestBody PspTrustAccountDto dto) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspTrustAccount> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspTrustAccount e = opt.get();
        e.setBankId(dto.getBankId());
        e.setBankAccountNumber(dto.getBankAccountNumber());
        e.setTrustAccDrTypeCode(dto.getTrustAccDrTypeCode());
        e.setOrgReceivingDonation(dto.getOrgReceivingDonation());
        e.setSectorCode(dto.getSectorCode());
        e.setTrustAccIntUtilizedDetails(dto.getTrustAccIntUtilizedDetails());
        e.setOpeningBalance(dto.getOpeningBalance());
        e.setPrincipalAmount(dto.getPrincipalAmount());
        e.setInterestEarned(dto.getInterestEarned());
        e.setClosingBalance(dto.getClosingBalance());
        e.setInterestUtilized(dto.getInterestUtilized());
        e.setTrustFields(dto.getTrustFields());
        e.setAsOfDate(dto.getAsOfDate());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspTrustAccount> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
