package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.underwriting.SignalSeverity;
import com.posgateway.aml.model.underwriting.VerificationSignal;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Detects merchant-to-merchant linkage via shared identifiers and flags "reincarnation" —
 * a previously terminated/blocked owner returning under a new merchant that reuses an
 * identifier. This is one of the strongest organized-merchant-fraud controls: a fraud ring
 * often reuses a registration number, phone or website across entities.
 *
 * <p>Phase 2 matches on plaintext identifiers (registration number, contact phone, website).
 * Encrypted identifiers (settlement account) and hashed UBO identity are future extensions.
 * Produces {@link VerificationSignal}s consumed by {@link MerchantVerificationOrchestrator}.
 */
@Service
public class MerchantLinkageService {

    private static final Logger log = LoggerFactory.getLogger(MerchantLinkageService.class);

    private static final Set<String> ADVERSE_STATUSES = Set.of("BLOCKED", "TERMINATED", "REJECTED");

    private final MerchantRepository merchantRepository;
    private final BeneficialOwnerRepository beneficialOwnerRepository;

    public MerchantLinkageService(MerchantRepository merchantRepository,
                                  BeneficialOwnerRepository beneficialOwnerRepository) {
        this.merchantRepository = merchantRepository;
        this.beneficialOwnerRepository = beneficialOwnerRepository;
    }

    public List<VerificationSignal> findLinkages(Merchant merchant) {
        String ref = merchant.getMerchantId() != null ? String.valueOf(merchant.getMerchantId()) : "unknown";

        // linkedMerchantId -> set of shared attributes
        Map<Long, Set<String>> linkedAttributes = new LinkedHashMap<>();
        Map<Long, Merchant> linkedMerchants = new LinkedHashMap<>();

        collect(merchant, merchant.getRegistrationNumber(),
                merchantRepository::findByRegistrationNumber, "registrationNumber", linkedAttributes, linkedMerchants);
        collect(merchant, merchant.getContactEmail(),
                merchantRepository::findByContactEmail, "contactEmail", linkedAttributes, linkedMerchants);
        collect(merchant, merchant.getWebsite(),
                merchantRepository::findByWebsite, "website", linkedAttributes, linkedMerchants);

        // Shared UBO: a beneficial owner (by keyed national-ID / passport hash) appearing on
        // another merchant is a strong organized-fraud / reincarnation signal.
        collectSharedOwners(merchant, linkedAttributes, linkedMerchants);

        List<VerificationSignal> signals = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> e : linkedAttributes.entrySet()) {
            Long linkedId = e.getKey();
            String attrs = String.join(", ", new TreeSet<>(e.getValue()));
            Merchant linked = linkedMerchants.get(linkedId);
            String status = linked != null && linked.getStatus() != null ? linked.getStatus().toUpperCase() : "";

            if (ADVERSE_STATUSES.contains(status)) {
                // Reincarnation: reuse of an identifier from a terminated/blocked merchant.
                signals.add(VerificationSignal.of("MERCHANT_REINCARNATION", SignalSeverity.HIGH, "INTERNAL",
                        ref, true, "Shares [" + attrs + "] with " + status + " merchant " + linkedId
                                + " — possible reincarnation; requires explanation"));
                log.warn("Merchant {} shares [{}] with {} merchant {} (reincarnation signal)",
                        ref, attrs, status, linkedId);
            } else {
                signals.add(VerificationSignal.of("LINKED_MERCHANT_SHARED_IDENTIFIER", SignalSeverity.MEDIUM,
                        "INTERNAL", ref, false, "Shares [" + attrs + "] with merchant " + linkedId
                                + " (status " + (status.isEmpty() ? "UNKNOWN" : status) + ")"));
            }
        }
        return signals;
    }

    private void collectSharedOwners(Merchant self, Map<Long, Set<String>> linkedAttributes,
                                     Map<Long, Merchant> linkedMerchants) {
        List<BeneficialOwner> owners = self.getBeneficialOwners();
        if (owners == null) {
            return;
        }
        for (BeneficialOwner owner : owners) {
            matchOwners(self, owner.getNationalIdHash(),
                    beneficialOwnerRepository::findByNationalIdHash, linkedAttributes, linkedMerchants);
            matchOwners(self, owner.getPassportHash(),
                    beneficialOwnerRepository::findByPassportHash, linkedAttributes, linkedMerchants);
        }
    }

    private void matchOwners(Merchant self, String hash,
                             java.util.function.Function<String, List<BeneficialOwner>> finder,
                             Map<Long, Set<String>> linkedAttributes, Map<Long, Merchant> linkedMerchants) {
        if (hash == null || hash.isBlank()) {
            return;
        }
        for (BeneficialOwner other : finder.apply(hash)) {
            Merchant om = other != null ? other.getMerchant() : null;
            if (om == null || om.getMerchantId() == null || om.getMerchantId().equals(self.getMerchantId())) {
                continue;
            }
            linkedAttributes.computeIfAbsent(om.getMerchantId(), k -> new TreeSet<>()).add("sharedUBO");
            linkedMerchants.putIfAbsent(om.getMerchantId(), om);
        }
    }

    private void collect(Merchant self, String value, java.util.function.Function<String, List<Merchant>> finder,
                         String attribute, Map<Long, Set<String>> linkedAttributes,
                         Map<Long, Merchant> linkedMerchants) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (Merchant other : finder.apply(value)) {
            if (other == null || other.getMerchantId() == null
                    || other.getMerchantId().equals(self.getMerchantId())) {
                continue;
            }
            linkedAttributes.computeIfAbsent(other.getMerchantId(), k -> new TreeSet<>()).add(attribute);
            linkedMerchants.putIfAbsent(other.getMerchantId(), other);
        }
    }
}
