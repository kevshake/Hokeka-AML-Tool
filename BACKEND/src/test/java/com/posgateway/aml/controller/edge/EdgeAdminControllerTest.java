package com.posgateway.aml.controller.edge;

import com.posgateway.aml.dto.edge.EdgeNodeCreatedResponse;
import com.posgateway.aml.dto.edge.EdgeNodeRequestBody;
import com.posgateway.aml.dto.edge.EdgeNodeView;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.repository.edge.EdgeMetricsRecordRepository;
import com.posgateway.aml.service.edge.EdgeEnrollmentException;
import com.posgateway.aml.service.edge.EdgeEnrollmentService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tenant scoping, one-time-code handling and error mapping of the edge fleet admin API. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EdgeAdminControllerTest {

    @Mock private EdgeEnrollmentService enrollmentService;
    @Mock private EdgeMetricsRecordRepository metricsRepository;
    @Mock private PspIsolationService pspIsolationService;

    private EdgeAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new EdgeAdminController(enrollmentService, metricsRepository, pspIsolationService);
        User user = User.builder().username("psp-admin").build();
        when(pspIsolationService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void listingIsDelegatedToTheTenantScopedService() {
        when(enrollmentService.listVisibleNodes(7L)).thenReturn(List.of(node(1L, 7L)));

        var response = controller.list(7L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("acme-eu-1", response.getBody().get(0).edgeId());
        verify(enrollmentService).listVisibleNodes(7L);
    }

    @Test
    void requestingANodeSanitisesThePspIdAndReturnsTheOneTimeCode() {
        when(pspIsolationService.sanitizePspId(99L)).thenReturn(7L); // PSP user cannot override
        EdgeNode created = node(1L, 7L);
        created.setStatus(EdgeNodeStatus.PENDING);
        created.setEdgeId(null);
        when(enrollmentService.requestNode(eq(7L), eq("Acme EU 1"), eq("psp-admin")))
                .thenReturn(new EdgeEnrollmentService.EnrollmentRequestResult(
                        created, "RAW-CODE", Instant.now().plusSeconds(86_400)));

        var response = controller.request(new EdgeNodeRequestBody(99L, "Acme EU 1"));

        assertEquals(201, response.getStatusCode().value());
        EdgeNodeCreatedResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("RAW-CODE", body.enrollmentCode());
        assertTrue(body.notice().contains("cannot"));
        verify(enrollmentService).requestNode(7L, "Acme EU 1", "psp-admin");
    }

    @Test
    void aPlatformAdminMustNameThePspWhenRequestingANode() {
        when(pspIsolationService.sanitizePspId(null)).thenReturn(null);

        EdgeEnrollmentException e = assertThrows(EdgeEnrollmentException.class,
                () -> controller.request(new EdgeNodeRequestBody(null, "Unowned")));
        assertEquals(400, e.getStatus());
    }

    @Test
    void lifecycleActionsDelegateWithTheActingUsername() {
        when(enrollmentService.approve(1L, "psp-admin")).thenReturn(node(1L, 7L));
        when(enrollmentService.reject(1L, "psp-admin")).thenReturn(node(1L, 7L));
        when(enrollmentService.suspend(1L, "psp-admin")).thenReturn(node(1L, 7L));
        when(enrollmentService.revoke(1L, "psp-admin")).thenReturn(node(1L, 7L));

        assertEquals(200, controller.approve(1L).getStatusCode().value());
        assertEquals(200, controller.reject(1L).getStatusCode().value());
        assertEquals(200, controller.suspend(1L).getStatusCode().value());
        assertEquals(200, controller.revoke(1L).getStatusCode().value());

        verify(enrollmentService).approve(1L, "psp-admin");
        verify(enrollmentService).reject(1L, "psp-admin");
        verify(enrollmentService).suspend(1L, "psp-admin");
        verify(enrollmentService).revoke(1L, "psp-admin");
    }

    @Test
    void aCrossTenantNodeIdIsAnsweredWith403AndNoDetail() {
        doThrow(new SecurityException("Cannot access data from another PSP"))
                .when(enrollmentService).getNode(2L);

        assertThrows(SecurityException.class, () -> controller.detail(2L));

        var mapped = controller.handleIsolation(new SecurityException("Cannot access data from another PSP"));
        assertEquals(403, mapped.getStatusCode().value());
        assertEquals("Access denied", mapped.getBody().get("error"));
    }

    @Test
    void serviceRefusalsKeepTheirHttpStatus() {
        var mapped = controller.handleEnrollment(EdgeEnrollmentException.conflict("already pinned"));
        assertEquals(409, mapped.getStatusCode().value());
        assertEquals("already pinned", mapped.getBody().get("error"));
    }

    @Test
    void theViewNeverExposesTheEnrollmentCodeDigest() {
        EdgeNode n = node(1L, 7L);
        n.setEnrollmentCodeHash("deadbeef");
        EdgeNodeView view = EdgeNodeView.of(n);

        // The projection reports only that a code is outstanding — the digest itself has no
        // component on the record and can never reach a client.
        assertTrue(view.enrollmentCodeOutstanding());
        assertNull(view.lastSeenAt());
        assertEquals("NEVER_SEEN", view.health());
        assertEquals("HEALTHY", EdgeNodeView.of(activeSeenNow()).health());
    }

    @Test
    void detailIncludesHealthAndRecentAggregateMetrics() {
        when(enrollmentService.getNode(1L)).thenReturn(activeSeenNow());
        when(metricsRepository.findTop20ByEdgeNodeIdOrderByWindowEndDesc(1L)).thenReturn(List.of());

        var response = controller.detail(1L);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("node"));
        assertNotNull(response.getBody().get("recentMetrics"));
        verify(metricsRepository).findTop20ByEdgeNodeIdOrderByWindowEndDesc(1L);
    }

    private static EdgeNode node(Long id, Long pspId) {
        EdgeNode n = new EdgeNode();
        n.setId(id);
        n.setPspId(pspId);
        n.setEdgeId("acme-eu-1");
        n.setDisplayName("Acme EU 1");
        n.setStatus(EdgeNodeStatus.ACTIVE);
        n.setCreatedAt(Instant.now());
        return n;
    }

    private static EdgeNode activeSeenNow() {
        EdgeNode n = node(1L, 7L);
        n.setLastSeenAt(Instant.now());
        return n;
    }
}
