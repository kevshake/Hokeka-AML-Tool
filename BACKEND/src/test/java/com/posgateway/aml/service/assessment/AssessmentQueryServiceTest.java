package com.posgateway.aml.service.assessment;

import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.AssessmentTriggerType;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentQueryServiceTest {

    @Mock
    private AssessmentRepository assessmentRepository;
    @Mock
    private FindingRepository findingRepository;
    @Mock
    private PspIsolationService pspIsolationService;

    @InjectMocks
    private AssessmentQueryService assessmentQueryService;

    @Test
    void enforcesTenantIsolationOnAssessmentRead() {
        UUID id = UUID.randomUUID();
        Assessment assessment = new Assessment();
        assessment.setId(id);
        assessment.setPspId(7L);
        assessment.setTriggerType(AssessmentTriggerType.TXN);
        assessment.setTriggerRef("100");
        when(assessmentRepository.findById(id)).thenReturn(Optional.of(assessment));
        doThrow(new SecurityException("denied")).when(pspIsolationService).validatePspAccess(7L);

        assertThrows(SecurityException.class, () -> assessmentQueryService.getByAssessmentId(id));
        verify(pspIsolationService).validatePspAccess(7L);
    }

    @Test
    void returnsLatestAssessmentForTransaction() {
        Assessment assessment = new Assessment();
        UUID id = UUID.randomUUID();
        assessment.setId(id);
        assessment.setPspId(3L);
        assessment.setTriggerType(AssessmentTriggerType.TXN);
        assessment.setTriggerRef("55");
        assessment.setTxnId(55L);
        when(assessmentRepository.findFirstByTxnIdOrderByCreatedAtDesc(55L)).thenReturn(Optional.of(assessment));
        when(findingRepository.findByAssessmentIdOrderByCreatedAtAsc(id)).thenReturn(java.util.List.of());

        var dto = assessmentQueryService.getLatestByTransactionId(55L);
        assertEquals(id, dto.getAssessmentId());
        verify(pspIsolationService).validatePspAccess(3L);
    }

    @Test
    void notFoundWhenNoAssessmentForTxn() {
        when(assessmentRepository.findFirstByTxnIdOrderByCreatedAtDesc(404L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> assessmentQueryService.getLatestByTransactionId(404L));
    }
}
