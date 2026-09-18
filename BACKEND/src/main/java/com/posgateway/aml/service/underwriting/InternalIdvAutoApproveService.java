package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.underwriting.SignalSeverity;
import com.posgateway.aml.model.underwriting.VerificationSignal;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.MerchantRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Privacy-preserving internal IDV decision. It consumes verification evidence and keyed
 * lookup hashes only; no plaintext account or identity document is queried or logged.
 */
@Service
public class InternalIdvAutoApproveService {
    private static final Set<String> ADVERSE = Set.of("BLOCKED", "TERMINATED", "REJECTED");

    private final MerchantRepository merchantRepository;
    private final BeneficialOwnerRepository beneficialOwnerRepository;

    public InternalIdvAutoApproveService(MerchantRepository merchantRepository,
            BeneficialOwnerRepository beneficialOwnerRepository) {
        this.merchantRepository = merchantRepository;
        this.beneficialOwnerRepository = beneficialOwnerRepository;
    }

    public Evaluation evaluate(Merchant merchant, List<VerificationSignal> signals) {
        if (merchant == null || merchant.getCbkSettlementAccountHash() == null) {
            return Evaluation.denied("SETTLEMENT_HASH_MISSING");
        }
        List<BeneficialOwner> owners = merchant.getBeneficialOwners();
        if (owners == null || owners.isEmpty() || owners.stream().anyMatch(this::missingIdentityHash)) {
            return Evaluation.denied("UBO_HASH_EVIDENCE_INCOMPLETE");
        }
        if (signals != null && signals.stream().anyMatch(signal ->
                signal.isRequiresManualReview()
                        || signal.getSeverity() == SignalSeverity.HIGH
                        || signal.getSeverity() == SignalSeverity.CRITICAL)) {
            return Evaluation.denied("ADVERSE_VERIFICATION_SIGNAL");
        }
        if (hasAdverseSettlementLink(merchant) || owners.stream().anyMatch(owner -> hasAdverseOwnerLink(merchant, owner))) {
            return Evaluation.denied("ADVERSE_CROSS_PSP_HASH_LINK");
        }
        return new Evaluation(true, "RIGOROUS_INTERNAL_IDV_CLEAR");
    }

    private boolean missingIdentityHash(BeneficialOwner owner) {
        return (owner.getNationalIdHash() == null || owner.getNationalIdHash().isBlank())
                && (owner.getPassportHash() == null || owner.getPassportHash().isBlank());
    }

    private boolean hasAdverseSettlementLink(Merchant self) {
        return merchantRepository.findByCbkSettlementAccountHash(self.getCbkSettlementAccountHash()).stream()
                .anyMatch(other -> isOtherAdverse(self, other));
    }

    private boolean hasAdverseOwnerLink(Merchant self, BeneficialOwner owner) {
        return linkedOwners(owner.getNationalIdHash(), true).stream()
                .anyMatch(linked -> isOtherAdverse(self, linked.getMerchant()))
                || linkedOwners(owner.getPassportHash(), false).stream()
                .anyMatch(linked -> isOtherAdverse(self, linked.getMerchant()));
    }

    private List<BeneficialOwner> linkedOwners(String hash, boolean nationalId) {
        if (hash == null || hash.isBlank()) return List.of();
        return nationalId ? beneficialOwnerRepository.findByNationalIdHash(hash)
                : beneficialOwnerRepository.findByPassportHash(hash);
    }

    private boolean isOtherAdverse(Merchant self, Merchant other) {
        if (other == null || other.getMerchantId() == null
                || other.getMerchantId().equals(self.getMerchantId())) return false;
        Long selfPsp = self.getPsp() == null ? null : self.getPsp().getPspId();
        Long otherPsp = other.getPsp() == null ? null : other.getPsp().getPspId();
        return !java.util.Objects.equals(selfPsp, otherPsp)
                && other.getStatus() != null && ADVERSE.contains(other.getStatus().toUpperCase());
    }

    public record Evaluation(boolean approved, String reasonCode) {
        static Evaluation denied(String reason) {
            return new Evaluation(false, reason);
        }
    }
}
