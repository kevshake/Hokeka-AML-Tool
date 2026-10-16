package com.posgateway.aml.service.assessment;

import com.posgateway.aml.entity.assessment.Finding;
import com.posgateway.aml.entity.assessment.FindingSourceType;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindingRecorderTest {

    @Mock
    private FindingRepository findingRepository;

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private TransactionReferenceResolver transactionReferences;

    @InjectMocks
    private FindingRecorder findingRecorder;

    @AfterEach
    void tearDown() {
        AssessmentRecordingScope.clear();
    }

    @Test
    void persistsFindingLinkedToAssessmentScope() {
        ReflectionTestUtils.setField(findingRecorder, "recordingEnabled", true);
        ReflectionTestUtils.setField(findingRecorder, "batchSize", 1);

        UUID assessmentId = UUID.randomUUID();
        AssessmentRecordingScope.set(new AssessmentRecordingScope.Scope(assessmentId, 2L, 99L, 4L));

        when(findingRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        Assessment assessment = new Assessment();
        assessment.setId(assessmentId);
        when(assessmentRepository.findAllById(java.util.Set.of(assessmentId))).thenReturn(List.of(assessment));
        UUID edgeId = UUID.randomUUID();
        when(transactionReferences.forAssessment(assessment))
                .thenReturn(new TransactionReference(99L, edgeId, "ORD-99", null));

        findingRecorder.record(FindingDraft.create()
                .sourceType(FindingSourceType.LIMIT)
                .sourceId("transaction_limits")
                .triggered(false)
                .explanation("Within limits"));
        findingRecorder.drainForTests();

        ArgumentCaptor<List<Finding>> captor = ArgumentCaptor.forClass(List.class);
        verify(findingRepository).saveAll(captor.capture());
        Finding saved = captor.getValue().get(0);
        assertEquals(assessmentId, saved.getAssessmentId());
        assertEquals(99L, saved.getTxnId());
        assertEquals(FindingSourceType.LIMIT, saved.getSourceType());
        assertTrue(saved.isShadow());
        // R8: the finding carries its transaction's ID reference.
        assertEquals(edgeId, saved.getEdgeAssessmentId());
        assertEquals("ORD-99", saved.getTxnRef());
    }
}
