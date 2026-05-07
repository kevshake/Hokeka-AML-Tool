package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspTariffTemplateDto;
import com.posgateway.aml.entity.psp.cbk.PspTariffTemplate;
import com.posgateway.aml.repository.psp.cbk.PspTariffTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * CBK GDI – Payment Gateway Tariff Templates (monthly).
 * Endpoint: /api/v1/psps/{pspId}/cbk/tariff-templates
 */
@RestController
@RequestMapping("/api/v1/psps/{pspId}/cbk/tariff-templates")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspTariffTemplateController {

    private static final Logger log = LoggerFactory.getLogger(PspTariffTemplateController.class);

    private final PspTariffTemplateRepository repository;

    public PspTariffTemplateController(PspTariffTemplateRepository repository) {
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
    public ResponseEntity<List<PspTariffTemplate>> list(@PathVariable Long pspId) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/active")
    public ResponseEntity<List<PspTariffTemplate>> listActive(@PathVariable Long pspId) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findActiveByPspId(pspId, LocalDate.now()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspTariffTemplate> getById(@PathVariable Long pspId, @PathVariable Long id) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspTariffTemplate> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspTariffTemplate> create(@PathVariable Long pspId,
                                                    @RequestBody PspTariffTemplateDto dto) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        PspTariffTemplate e = PspTariffTemplate.builder()
                .pspId(pspId)
                .channelUsed(dto.getChannelUsed())
                .channelPartnerName(dto.getChannelPartnerName())
                .chargeDescription(dto.getChargeDescription())
                .percentageTransactionCost(dto.getPercentageTransactionCost())
                .absoluteTransactionCost(dto.getAbsoluteTransactionCost())
                .effectiveFrom(dto.getEffectiveFrom())
                .effectiveTo(dto.getEffectiveTo())
                .build();
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspTariffTemplate> update(@PathVariable Long pspId,
                                                    @PathVariable Long id,
                                                    @RequestBody PspTariffTemplateDto dto) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspTariffTemplate> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspTariffTemplate e = opt.get();
        e.setChannelUsed(dto.getChannelUsed());
        e.setChannelPartnerName(dto.getChannelPartnerName());
        e.setChargeDescription(dto.getChargeDescription());
        e.setPercentageTransactionCost(dto.getPercentageTransactionCost());
        e.setAbsoluteTransactionCost(dto.getAbsoluteTransactionCost());
        e.setEffectiveFrom(dto.getEffectiveFrom());
        e.setEffectiveTo(dto.getEffectiveTo());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspTariffTemplate> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
