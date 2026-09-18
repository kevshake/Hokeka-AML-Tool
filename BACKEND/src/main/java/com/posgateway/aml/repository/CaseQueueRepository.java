package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.CaseQueue;
import com.posgateway.aml.model.CasePriority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaseQueueRepository extends JpaRepository<CaseQueue, Long> {
    Optional<CaseQueue> findByQueueName(String queueName);

    Optional<CaseQueue> findByMinPriority(CasePriority minPriority);

    java.util.List<CaseQueue> findByAutoAssignTrueAndEnabledTrue();
}
