package com.posgateway.aml.repository;

import com.posgateway.aml.entity.merchant.ExternalAmlResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for External AML Response entity
 */
@Repository
public interface ExternalAmlResponseRepository extends JpaRepository<ExternalAmlResponse, Long> {

    /**
     * Find responses by merchant ID
     */
    List<ExternalAmlResponse> findByMerchant_MerchantId(Long merchantId);

    /**
     * Find responses by provider name
     */
    List<ExternalAmlResponse> findByProviderName(String providerName);

    /**
     * Find responses with sanctions matches
     */
    List<ExternalAmlResponse> findBySanctionsMatchTrue();

    /**
     * Find responses with PEP matches
     */
    List<ExternalAmlResponse> findByPepMatchTrue();
}
