package com.posgateway.aml.repository.crypto;

import com.posgateway.aml.entity.crypto.TravelRuleTransmissionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRuleTransmissionAttemptRepository extends JpaRepository<TravelRuleTransmissionAttempt, Long> {
    long countByTransferId(Long transferId);
}
