package com.posgateway.aml.service.compliance;

import com.posgateway.aml.dto.compliance.CbkSubmitRequest;
import com.posgateway.aml.dto.compliance.CbkSubmitResponse;
import com.posgateway.aml.repository.compliance.CbkSubmissionRepository;
import com.posgateway.aml.service.cbk.CbkEndpointType;
import com.posgateway.aml.service.cbk.CbkSubmissionOrchestrator;
import com.posgateway.aml.service.cbk.CbkSubmissionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CbkReportServiceTest {

    @Mock private CbkSubmissionRepository repository;
    @Mock private CbkSubmissionOrchestrator orchestrator;

    private CbkReportService service;

    @BeforeEach
    void setUp() {
        service = new CbkReportService(repository, orchestrator);
    }

    @Test
    void submitExecutesExactEndpointWithoutCreatingPlaceholderRow() {
        when(orchestrator.runSingleEndpoint(9L, CbkEndpointType.CARD_BRANDS))
                .thenReturn(CbkSubmissionResult.builder()
                        .pspId(9L)
                        .endpointType(CbkEndpointType.CARD_BRANDS)
                        .outcome(CbkSubmissionResult.Outcome.SUCCESS)
                        .httpStatus(200)
                        .referenceNumber("CBK-REQ-9")
                        .submissionId(81L)
                        .build());

        CbkSubmitResponse response = service.submitReport(
                9L,
                3L,
                new CbkSubmitRequest(
                        "CARD_BRANDS", null, "monthly",
                        "2026-06-01", "2026-06-30", Map.of()));

        assertEquals("submitted", response.status());
        assertEquals("CBK-REQ-9", response.referenceNumber());
        verify(orchestrator).runSingleEndpoint(9L, CbkEndpointType.CARD_BRANDS);
        verify(repository, never()).save(any());
    }

    @Test
    void submitRejectsGenericLegacyReportIdentifier() {
        CbkSubmitRequest request = new CbkSubmitRequest(
                null, "cbk-returns", "monthly", null, null, Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(9L, 3L, request));
        verifyNoInteractions(orchestrator);
    }
}
