package com.posgateway.aml.service.kyc;

import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.repository.BeneficialOwnerRepository;
import com.posgateway.aml.service.security.PiiLookupHasher;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Encrypts legacy plaintext UBO identifiers while backfilling keyed lookup hashes. */
@Service
public class UboPiiMigrationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UboPiiMigrationService.class);
    private final BeneficialOwnerRepository repository;
    private final PiiLookupHasher hasher;

    public UboPiiMigrationService(BeneficialOwnerRepository repository, PiiLookupHasher hasher) {
        this.repository = repository;
        this.hasher = hasher;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyIdentifiers() {
        if (!hasher.isConfigured()) {
            log.warn("PII lookup key is not configured; legacy UBO identifier migration was skipped");
            return;
        }
        List<BeneficialOwner> changed = new ArrayList<>();
        for (BeneficialOwner owner : repository.findAll()) {
            boolean needsUpdate = false;
            if (owner.getPassportNumber() != null && owner.getPassportHash() == null) {
                owner.setPassportHash(hasher.hashIdentifier(owner.getPassportNumber()));
                needsUpdate = true;
            }
            if (owner.getNationalId() != null && owner.getNationalIdHash() == null) {
                owner.setNationalIdHash(hasher.hashIdentifier(owner.getNationalId()));
                needsUpdate = true;
            }
            if (needsUpdate) changed.add(owner);
        }
        if (!changed.isEmpty()) {
            repository.saveAll(changed);
            repository.flush();
            log.info("Encrypted and indexed identifiers for {} legacy beneficial-owner rows", changed.size());
        }
    }
}
