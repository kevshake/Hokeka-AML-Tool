package com.posgateway.aml.controller.risk;




import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.risk.TransactionLimitService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/risk/limits")
public class TransactionLimitController {

    private final TransactionLimitService transactionLimitService;
    private final MerchantRepository merchantRepository;

    public TransactionLimitController(TransactionLimitService transactionLimitService, MerchantRepository merchantRepository) {
        this.transactionLimitService = transactionLimitService;
        this.merchantRepository = merchantRepository;
    }


    @PutMapping("/merchant/{merchantId}/temporary")
    public ResponseEntity<Void> setTemporaryLimit(
            @PathVariable Long merchantId,
            @RequestParam BigDecimal amount,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiry) {

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        transactionLimitService.setTemporaryLimit(merchant, amount, expiry);
        return ResponseEntity.ok().build();
    }
}
