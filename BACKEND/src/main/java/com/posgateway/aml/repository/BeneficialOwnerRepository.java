package com.posgateway.aml.repository;

import com.posgateway.aml.entity.merchant.BeneficialOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BeneficialOwnerRepository extends JpaRepository<BeneficialOwner, Long> {

    List<BeneficialOwner> findByMerchant_MerchantId(Long merchantId);

    /** Batch variant of {@link #findByMerchant_MerchantId} for listing endpoints (avoids per-merchant N+1). */
    List<BeneficialOwner> findByMerchant_MerchantIdIn(java.util.Collection<Long> merchantIds);

    // Encrypted fields searching usually requires exact match on hash or decryption
    // layer.
    // Assuming for searching we have a hash column or strict access.
    // For this implementation, we assume strict match on the field available.

    List<BeneficialOwner> findByPassportHash(String passportHash);

    List<BeneficialOwner> findByNationalIdHash(String nationalIdHash);

    @Query("SELECT b FROM BeneficialOwner b WHERE b.fullName = :fullName AND b.dateOfBirth = :dob")
    List<BeneficialOwner> findPotentialDuplicates(@Param("fullName") String fullName,
            @Param("dob") java.time.LocalDate dob);
}
