package com.posgateway.aml.repository.compliance;

import com.posgateway.aml.entity.compliance.EvidenceChainOfCustody;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenceChainOfCustodyRepository extends JpaRepository<EvidenceChainOfCustody, Long> {
    List<EvidenceChainOfCustody> findByEvidenceIdOrderByTimestampAsc(Long evidenceId);
}

