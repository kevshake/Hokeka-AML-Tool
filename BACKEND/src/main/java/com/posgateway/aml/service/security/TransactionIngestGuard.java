package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Request-level checks for {@code POST /transactions/ingest[/batch]}, run <b>before</b> anything is
 * persisted:
 * <ul>
 *   <li>the merchant id is a numeric platform id (400 otherwise);</li>
 *   <li>the merchant exists and belongs to the caller's PSP — PSP users and PSP API keys can only
 *       ingest for their own merchants; platform administrators for any. A merchant of another PSP
 *       is reported exactly like a missing one (404) so tenants cannot probe each other's ids;</li>
 *   <li>an {@code edgeAssessmentId}, when present, is a UUID (400 otherwise).</li>
 * </ul>
 * Errors are {@link IllegalArgumentException}s whose messages {@code GlobalExceptionHandler} maps to
 * 400 / 404 JSON, or {@link SecurityException} (403) when there is no authenticated caller.
 */
@Service
public class TransactionIngestGuard {

    private static final Logger log = LoggerFactory.getLogger(TransactionIngestGuard.class);

    private final MerchantRepository merchantRepository;
    private final PspIsolationService pspIsolationService;

    public TransactionIngestGuard(MerchantRepository merchantRepository, PspIsolationService pspIsolationService) {
        this.merchantRepository = merchantRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * Resolves the merchant and enforces that the caller may ingest for it.
     *
     * @return the merchant, owned by the caller's PSP (or any merchant for a platform admin)
     */
    @Transactional(readOnly = true)
    public Merchant requireIngestableMerchant(String rawMerchantId) {
        if (rawMerchantId == null || rawMerchantId.isBlank()) {
            throw new IllegalArgumentException("merchantId is required");
        }
        long merchantId;
        try {
            merchantId = Long.parseLong(rawMerchantId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("merchantId must be a numeric platform merchant ID");
        }
        User caller = pspIsolationService.getCurrentUser();
        if (caller == null) {
            throw new SecurityException("User not authenticated");
        }
        Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
        if (merchant == null) {
            throw new IllegalArgumentException("Merchant not found: " + merchantId);
        }
        if (pspIsolationService.isPlatformAdministrator(caller)) {
            return merchant;
        }
        Long callerPsp = caller.getPsp() == null ? null : caller.getPsp().getPspId();
        Long merchantPsp = merchant.getPsp() == null ? null : merchant.getPsp().getPspId();
        if (callerPsp == null || !callerPsp.equals(merchantPsp)) {
            log.warn("Ingest refused: caller {} (psp {}) used merchant {} of psp {}",
                    caller.getUsername(), callerPsp, merchantId, merchantPsp);
            throw new IllegalArgumentException("Merchant not found: " + merchantId);
        }
        return merchant;
    }

    /** Validates an optional edge assessment id (must be a UUID minted by the Edge Node). */
    public void requireValidEdgeAssessmentId(String edgeAssessmentId) {
        if (edgeAssessmentId == null || edgeAssessmentId.isBlank()) {
            return;
        }
        try {
            UUID.fromString(edgeAssessmentId.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("edgeAssessmentId must be a UUID");
        }
    }
}
