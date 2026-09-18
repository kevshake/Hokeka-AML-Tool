package com.posgateway.aml.repository.multiasset;

import com.posgateway.aml.entity.multiasset.CustomerRelationshipType;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomerRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MultiAssetCustomerRelationshipRepository
        extends JpaRepository<MultiAssetCustomerRelationship, Long> {
    List<MultiAssetCustomerRelationship> findByCustomerIdAndPspIdOrderByCreatedAtAsc(Long customerId, Long pspId);
    Optional<MultiAssetCustomerRelationship> findByPspIdAndCustomerIdAndRelatedCustomerIdAndRelationshipType(
            Long pspId, Long customerId, Long relatedCustomerId, CustomerRelationshipType relationshipType);
}

