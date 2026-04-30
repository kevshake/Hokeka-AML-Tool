package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.compliance.CaseEvidence;
import com.posgateway.aml.entity.compliance.EvidenceChainOfCustody;
import com.posgateway.aml.repository.compliance.CaseEvidenceRepository;
import com.posgateway.aml.repository.compliance.EvidenceChainOfCustodyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Evidence Chain of Custody Service
 * Tracks chain of custody for evidence
 */
@Service
public class EvidenceChainOfCustodyService {

    private static final Logger logger = LoggerFactory.getLogger(EvidenceChainOfCustodyService.class);

    private final CaseEvidenceRepository evidenceRepository;
    private final EvidenceChainOfCustodyRepository chainOfCustodyRepository;

    @Autowired
    public EvidenceChainOfCustodyService(
            CaseEvidenceRepository evidenceRepository,
            EvidenceChainOfCustodyRepository chainOfCustodyRepository) {
        this.evidenceRepository = evidenceRepository;
        this.chainOfCustodyRepository = chainOfCustodyRepository;
    }

    /**
     * Record chain of custody event
     */
    @Transactional
    public EvidenceChainOfCustody recordCustodyEvent(Long evidenceId, String action, Long userId, String notes) {
        CaseEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new RuntimeException("Evidence not found: " + evidenceId));

        EvidenceChainOfCustody custody = new EvidenceChainOfCustody();
        custody.setEvidence(evidence);
        custody.setAction(action); // VIEWED, DOWNLOADED, MODIFIED, TRANSFERRED, DELETED
        custody.setUserId(userId);
        custody.setNotes(notes);
        custody.setTimestamp(LocalDateTime.now());

        logger.info("Recorded chain of custody event for evidence {}: {} by user {}", 
                evidenceId, action, userId);
        return chainOfCustodyRepository.save(custody);
    }

    /**
     * Get chain of custody for evidence
     */
    public List<EvidenceChainOfCustody> getChainOfCustody(Long evidenceId) {
        return chainOfCustodyRepository.findByEvidenceIdOrderByTimestampAsc(evidenceId);
    }

    /**
     * Verify chain of custody integrity
     */
    public boolean verifyChainOfCustody(Long evidenceId) {
        List<EvidenceChainOfCustody> chain = getChainOfCustody(evidenceId);
        
        // Check for gaps or inconsistencies
        for (int i = 1; i < chain.size(); i++) {
            EvidenceChainOfCustody previous = chain.get(i - 1);
            EvidenceChainOfCustody current = chain.get(i);
            
            // Check for reasonable time gaps
            long hoursBetween = java.time.temporal.ChronoUnit.HOURS.between(
                    previous.getTimestamp(), current.getTimestamp());
            
            if (hoursBetween > 24 && !"TRANSFERRED".equals(current.getAction())) {
                logger.warn("Potential chain of custody gap for evidence {}: {} hours between events",
                        evidenceId, hoursBetween);
                return false;
            }
        }
        
        return true;
    }
}

