package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspProductDto;
import com.posgateway.aml.entity.psp.cbk.PspProduct;
import com.posgateway.aml.repository.psp.cbk.PspProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * CBK GDI – Products Info (monthly, day 1).
 * Endpoint: /api/v1/psps/{pspId}/cbk/products
 */
@RestController
@RequestMapping("/api/v1/psps/{pspId}/cbk/products")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspProductController {

    private static final Logger log = LoggerFactory.getLogger(PspProductController.class);

    private final PspProductRepository repository;

    public PspProductController(PspProductRepository repository) {
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
    public ResponseEntity<List<PspProduct>> list(@PathVariable Long pspId) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspProduct> getById(@PathVariable Long pspId, @PathVariable Long id) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspProduct> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspProduct> create(@PathVariable Long pspId,
                                             @RequestBody PspProductDto dto) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        PspProduct e = PspProduct.builder()
                .pspId(pspId)
                .productName(dto.getProductName())
                .productOwnershipFlag(dto.getProductOwnershipFlag())
                .productOwnershipCategory(dto.getProductOwnershipCategory())
                .productPartnerName(dto.getProductPartnerName())
                .productTransactionCode(dto.getProductTransactionCode())
                .genderSegment(dto.getGenderSegment())
                .statusCode(dto.getStatusCode())
                .bandCode(dto.getBandCode())
                .noOfCustomers(dto.getNoOfCustomers())
                .noOfTransactions(dto.getNoOfTransactions())
                .valueOfTransactions(dto.getValueOfTransactions())
                .build();
        return ResponseEntity.ok(repository.save(e));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspProduct> update(@PathVariable Long pspId,
                                             @PathVariable Long id,
                                             @RequestBody PspProductDto dto) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();

        Optional<PspProduct> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();

        PspProduct e = opt.get();
        e.setProductName(dto.getProductName());
        e.setProductOwnershipFlag(dto.getProductOwnershipFlag());
        e.setProductOwnershipCategory(dto.getProductOwnershipCategory());
        e.setProductPartnerName(dto.getProductPartnerName());
        e.setProductTransactionCode(dto.getProductTransactionCode());
        e.setGenderSegment(dto.getGenderSegment());
        e.setStatusCode(dto.getStatusCode());
        e.setBandCode(dto.getBandCode());
        e.setNoOfCustomers(dto.getNoOfCustomers());
        e.setNoOfTransactions(dto.getNoOfTransactions());
        e.setValueOfTransactions(dto.getValueOfTransactions());
        return ResponseEntity.ok(repository.save(e));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long pspId, @PathVariable Long id) {
        com.posgateway.aml.entity.User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspProduct> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
