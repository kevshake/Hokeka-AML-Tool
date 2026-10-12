package com.posgateway.aml.controller.assessment;

import com.posgateway.aml.dto.assessment.AssessmentDetailResponseDTO;
import com.posgateway.aml.service.assessment.AssessmentQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/assessments")
public class AssessmentController {

    private final AssessmentQueryService assessmentQueryService;

    public AssessmentController(AssessmentQueryService assessmentQueryService) {
        this.assessmentQueryService = assessmentQueryService;
    }

    @GetMapping("/{assessmentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE_OFFICER', 'ANALYST', 'PSP_ADMIN', 'PSP_ANALYST', 'PSP_USER', 'VIEWER')")
    public ResponseEntity<AssessmentDetailResponseDTO> getAssessment(@PathVariable UUID assessmentId) {
        return ResponseEntity.ok(assessmentQueryService.getByAssessmentId(assessmentId));
    }
}
