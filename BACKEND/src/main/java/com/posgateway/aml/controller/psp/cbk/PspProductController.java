package com.posgateway.aml.controller.psp.cbk;

import com.posgateway.aml.dto.psp.cbk.PspProductDto;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.cbk.PspProduct;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.psp.cbk.PspProductRepository;
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
 * CBK GDI – Products Info (monthly, day 1).
 * Endpoint: /api/v1/psps/{pspId}/cbk/products
 */
@RestController
@RequestMapping("/psps/{pspId}/cbk/products")
@PreAuthorize("hasAnyRole('ADMIN','PSP_ADMIN','COMPLIANCE_OFFICER')")
public class PspProductController {

    private static final Logger log = LoggerFactory.getLogger(PspProductController.class);

    private final PspProductRepository repository;

    public PspProductController(PspProductRepository repository) {
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
    public ResponseEntity<List<PspProduct>> list(@PathVariable Long pspId) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(repository.findByPspId(pspId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PspProduct> getById(@PathVariable Long pspId, @PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspProduct> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<PspProduct> create(@PathVariable Long pspId,
                                             @RequestBody PspProductDto dto) {
        User user = getCurrentUser();
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
        User user = getCurrentUser();
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
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (!canAccess(user, pspId)) return ResponseEntity.status(403).build();
        Optional<PspProduct> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getPspId().equals(pspId)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
