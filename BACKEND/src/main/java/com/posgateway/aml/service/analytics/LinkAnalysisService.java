package com.posgateway.aml.service.analytics;



import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// @RequiredArgsConstructor removed
@Service
public class LinkAnalysisService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LinkAnalysisService.class);

    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;

    public LinkAnalysisService(MerchantRepository merchantRepository, TransactionRepository transactionRepository) {
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
    }


    /**
     * Check if the transaction is linked to any previously BLOCKED or TERMINATED
     * entities
     * via shared attributes (IP, Device)
     * 
     * @param transaction current transaction
     * @param merchant    current merchant
     * @return List of linked blocked merchant IDs, or empty
     */
    public List<String> findLinkedBlockedEntities(Transaction transaction, Merchant merchant) {
        log.debug("Performing Link Analysis for Transaction: {}", transaction.getTransactionId());

        List<String> linkedBlockedMerchants = new ArrayList<>();

        // 1. Check Device Fingerprint Links
        if (transaction.getDeviceFingerprint() != null && !transaction.getDeviceFingerprint().isEmpty()) {
            List<String> linkedMerchantIds = transactionRepository
                    .findMerchantIdsByDeviceFingerprint(transaction.getDeviceFingerprint());
            checkBlocked(linkedMerchantIds, merchant.getMerchantId(), linkedBlockedMerchants, "Device Fingerprint");
        }

        // 2. Check IP Address Links
        if (transaction.getIpAddress() != null && !transaction.getIpAddress().isEmpty()) {
            List<String> linkedMerchantIds = transactionRepository
                    .findMerchantIdsByIpAddress(transaction.getIpAddress());
            checkBlocked(linkedMerchantIds, merchant.getMerchantId(), linkedBlockedMerchants, "IP Address");
        }

        return linkedBlockedMerchants;
    }

    private void checkBlocked(List<String> linkedMerchantIds, Long currentMerchantId, List<String> results,
            String type) {
        for (String linkedIdStr : linkedMerchantIds) {
            try {
                Long linkedId = Long.parseLong(linkedIdStr);
                // Skip self
                if (linkedId.equals(currentMerchantId))
                    continue;

                merchantRepository.findById(linkedId).ifPresent(linkedMerchant -> {
                    String status = linkedMerchant.getStatus();
                    if ("BLOCKED".equalsIgnoreCase(status) || "TERMINATED".equalsIgnoreCase(status)) {
                        log.warn("Link Analysis Match: Current Merchant {} is linked to {} Merchant {} via {}",
                                currentMerchantId, status, linkedId, type);
                        results.add(linkedIdStr); // Return specific ID or just a flag
                    }
                });
            } catch (NumberFormatException e) {
                // Ignore invalid IDs
            }
        }
    }
}
