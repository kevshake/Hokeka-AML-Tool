package com.posgateway.aml.controller.psp;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.billing.PspPaymentMethod;
import com.posgateway.aml.repository.PspPaymentMethodRepository;
import com.posgateway.aml.repository.PspRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/billing/payment-methods")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PLATFORM_ADMIN','ROLE_PSP_ADMIN')")
public class CardPaymentMethodController {
    private final PspPaymentMethodRepository methods;
    private final PspRepository psps;

    public CardPaymentMethodController(PspPaymentMethodRepository methods, PspRepository psps) {
        this.methods = methods;
        this.psps = psps;
    }

    @PostMapping
    public ResponseEntity<PspPaymentMethod> save(@RequestParam Long pspId,
            @RequestBody PspPaymentMethod method, @AuthenticationPrincipal User user) {
        if (!canAccess(user, pspId) || method.getTokenVaultRef() == null
                || method.getLast4() == null || method.getBrand() == null
                || method.getExpiryMonth() == null || method.getExpiryYear() == null) {
            return ResponseEntity.badRequest().build();
        }
        return psps.findById(pspId).map(psp -> {
            method.setPsp(psp);
            method.setActive(true);
            return ResponseEntity.ok(methods.save(method));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return methods.findById(id).map(method -> {
            Long pspId = method.getPsp().getPspId();
            if (!canAccess(user, pspId)) return ResponseEntity.status(403).<Void>build();
            method.setActive(false);
            methods.save(method);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private boolean canAccess(User user, Long pspId) {
        if (user == null) return false;
        if (user.getPsp() == null) return true;
        return pspId.equals(user.getPsp().getPspId());
    }
}
