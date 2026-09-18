package com.posgateway.aml.repository.edd;

import com.posgateway.aml.entity.edd.EddEvidenceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EddEvidenceEventRepository extends JpaRepository<EddEvidenceEvent, Long> {
    List<EddEvidenceEvent> findByEddRequestIdOrderByOccurredAtDesc(Long eddRequestId);
}
