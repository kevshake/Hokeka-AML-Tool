package com.posgateway.aml.service.assessment;

import com.posgateway.aml.dto.assessment.AssessmentDetailResponseDTO;
import com.posgateway.aml.dto.assessment.FindingResponseDTO;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.Finding;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import com.posgateway.aml.service.ai.decision.AiDisclosureSanitizer;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AssessmentQueryService {

    private final AssessmentRepository assessmentRepository;
    private final FindingRepository findingRepository;
    private final PspIsolationService pspIsolationService;

    public AssessmentQueryService(AssessmentRepository assessmentRepository,
                                  FindingRepository findingRepository,
                                  PspIsolationService pspIsolationService) {
        this.assessmentRepository = assessmentRepository;
        this.findingRepository = findingRepository;
        this.pspIsolationService = pspIsolationService;
    }

    public AssessmentDetailResponseDTO getByAssessmentId(UUID assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found"));
        pspIsolationService.validatePspAccess(assessment.getPspId());
        return toDetail(assessment, true);
    }

    public AssessmentDetailResponseDTO getLatestByTransactionId(Long txnId) {
        Assessment assessment = assessmentRepository.findFirstByTxnIdOrderByCreatedAtDesc(txnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No assessment for transaction " + txnId));
        pspIsolationService.validatePspAccess(assessment.getPspId());
        return toDetail(assessment, true);
    }

    private AssessmentDetailResponseDTO toDetail(Assessment assessment, boolean includeFindings) {
        AssessmentDetailResponseDTO dto = new AssessmentDetailResponseDTO();
        dto.setAssessmentId(assessment.getId());
        dto.setPspId(assessment.getPspId());
        dto.setTriggerType(assessment.getTriggerType().name());
        dto.setTriggerRef(assessment.getTriggerRef());
        dto.setTxnId(assessment.getTxnId());
        dto.setParentAssessmentId(assessment.getParentAssessmentId());
        dto.setEdgeAssessmentId(assessment.getEdgeAssessmentId());
        dto.setVersions(assessment.getVersions());
        dto.setContextHash(assessment.getContextHash());
        dto.setDecision(assessment.getDecision());
        dto.setLatencyMs(assessment.getLatencyMs());
        dto.setCreatedAt(assessment.getCreatedAt());
        if (includeFindings) {
            List<Finding> findings = findingRepository.findByAssessmentIdOrderByCreatedAtAsc(assessment.getId());
            dto.setFindings(findings.stream().map(this::toFindingDto).toList());
        }
        return dto;
    }

    private FindingResponseDTO toFindingDto(Finding finding) {
        FindingResponseDTO dto = new FindingResponseDTO();
        dto.setFindingId(finding.getId());
        dto.setAssessmentId(finding.getAssessmentId());
        dto.setTxnId(finding.getTxnId());
        dto.setPartyId(finding.getPartyId());
        dto.setSourceType(finding.getSourceType().name());
        dto.setSourceId(finding.getSourceId());
        dto.setSourceVersion(finding.getSourceVersion());
        dto.setPhase(finding.getPhase().name());
        dto.setShadow(finding.isShadow());
        dto.setNature(finding.getNature());
        dto.setSeverity(finding.getSeverity());
        dto.setTriggered(finding.isTriggered());
        dto.setScore(finding.getScore());
        dto.setProposedAction(finding.getProposedAction());
        dto.setProposedActions(finding.getProposedActions());
        dto.setEvidence(finding.getEvidence());
        dto.setFeatureReferences(finding.getFeatureReferences());
        String explanation = finding.getExplanation();
        if (explanation != null) {
            AiDisclosureSanitizer.assertPspSafe("assessment finding explanation", explanation);
        }
        dto.setExplanation(explanation);
        dto.setCreatedAt(finding.getCreatedAt());
        return dto;
    }
}
