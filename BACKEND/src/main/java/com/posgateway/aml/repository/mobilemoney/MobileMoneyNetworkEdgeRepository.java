package com.posgateway.aml.repository.mobilemoney;

import com.posgateway.aml.entity.mobilemoney.MobileMoneyEntityType;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyNetworkEdge;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyRelationshipType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MobileMoneyNetworkEdgeRepository extends JpaRepository<MobileMoneyNetworkEdge, Long> {
    Optional<MobileMoneyNetworkEdge> findByPspIdAndFromEntityTypeAndFromEntityReferenceAndToEntityTypeAndToEntityReferenceAndRelationshipType(
            Long pspId, MobileMoneyEntityType fromType, String fromReference, MobileMoneyEntityType toType,
            String toReference, MobileMoneyRelationshipType relationshipType);

    List<MobileMoneyNetworkEdge> findTop200ByPspIdAndFromEntityTypeAndFromEntityReferenceOrderByLastSeenAtDesc(
            Long pspId, MobileMoneyEntityType entityType, String entityReference);

    List<MobileMoneyNetworkEdge> findTop200ByPspIdAndToEntityTypeAndToEntityReferenceOrderByLastSeenAtDesc(
            Long pspId, MobileMoneyEntityType entityType, String entityReference);
}
