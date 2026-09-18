package com.posgateway.aml.repository.chargeback;

import com.posgateway.aml.entity.chargeback.VerifiDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerifiDecisionRepository extends JpaRepository<VerifiDecision, Long> {

    Optional<VerifiDecision> findTopByCaseIdOrderByCreatedAtDesc(String caseId);

    List<VerifiDecision> findByPspTransactionIdOrderByCreatedAtDesc(String pspTransactionId);
}
