package com.posgateway.aml.controller.risk;




import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.risk.TransactionLimitService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// @RequiredArgsConstructor removed
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','PSP_USER')")
@RestController
@RequestMapping("/risk/limits")
public class TransactionLimitController {

    private final TransactionLimitService transactionLimitService;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final PspIsolationService pspIsolationService;

    public TransactionLimitController(TransactionLimitService transactionLimitService,
            MerchantRepository merchantRepository, UserRepository userRepository,
            PspIsolationService pspIsolationService) {
        this.transactionLimitService = transactionLimitService;
        this.merchantRepository = merchantRepository;
        this.userRepository = userRepository;
        this.pspIsolationService = pspIsolationService;
    }

    private User requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    @PutMapping("/merchant/{merchantId}/temporary")
    public ResponseEntity<Void> setTemporaryLimit(
            @PathVariable Long merchantId,
            @RequestParam BigDecimal amount,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiry) {

        Long userId = requireCurrentUser().getId();
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));

        // Tenant isolation: a PSP user may only set limits on their own PSP's merchants.
        // Platform admins pass. Throws SecurityException (→403) on cross-tenant attempt.
        pspIsolationService.validateMerchantAccess(merchant);

        transactionLimitService.setTemporaryLimit(merchant, amount, expiry, userId);
        return ResponseEntity.ok().build();
    }
}
