package com.posgateway.aml.service.filing;

import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.filing.LegalHoldWorkflow;
import com.posgateway.aml.entity.filing.RegulatoryFiledDocument;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceDeadlineRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.filing.RegulatoryFiledDocumentRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.PermissionService;
import com.posgateway.aml.service.SarWorkflowService;
import com.posgateway.aml.service.edgedata.EdgeDataCaller;
import com.posgateway.aml.service.edgedata.EdgeDataCallerResolver;
import com.posgateway.aml.service.edgedata.EdgeDataScope;
import com.posgateway.aml.service.legalhold.LegalHoldService;
import com.posgateway.aml.service.residency.PspDataSettingsService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Filing orchestration: roles, maker-checker state, refusals that change nothing, and the success path (R9). */
class RegulatoryFilingServiceTest {

    private static final UUID A = UUID.fromString("c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f");
    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");

    /** Transaction manager that does nothing (the repositories are mocks). */
    static final class NoTx extends AbstractPlatformTransactionManager {
        /** A fresh transaction object. */
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        /** Nothing to begin. */
        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        /** Nothing to commit. */
        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        /** Nothing to roll back. */
        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private final SuspiciousActivityReportRepository sars = mock(SuspiciousActivityReportRepository.class);
    private final ComplianceDeadlineRepository deadlines = mock(ComplianceDeadlineRepository.class);
    private final AlertRepository alerts = mock(AlertRepository.class);
    private final RegulatoryFiledDocumentRepository documents = mock(RegulatoryFiledDocumentRepository.class);
    private final SarWorkflowService workflow = mock(SarWorkflowService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final PspIsolationService isolation = mock(PspIsolationService.class);
    private final EdgeDataCallerResolver callers = mock(EdgeDataCallerResolver.class);
    private final FilingDataService data = mock(FilingDataService.class);
    private final GoAmlFilingRenderer renderer = new GoAmlFilingRenderer("E", "N", "KES");
    private final FiledDocumentStore store = mock(FiledDocumentStore.class);
    private final LegalHoldService holds = mock(LegalHoldService.class);
    private final PspDataSettingsService settings = mock(PspDataSettingsService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private RegulatoryFilingService service;
    private User mlro;

    @BeforeEach
    void setUp() {
        service = new RegulatoryFilingService(sars, deadlines, alerts, documents, workflow, permissions, isolation,
                callers, data, renderer, store, holds, settings, audit, new NoTx(), Clock.fixed(NOW, ZoneOffset.UTC));
        mlro = user(12L, 45L, "MLRO");
        when(isolation.getCurrentUser()).thenReturn(mlro);
        when(permissions.hasPermission(any(Role.class), eq(Permission.FILE_SAR))).thenReturn(true);
        when(settings.retentionState(45L)).thenReturn(new PspDataSettingsService.RetentionState(7, null, null));
        EdgeDataCaller caller = new EdgeDataCaller("u-12", "MLRO", 45L, false);
        when(callers.current()).thenReturn(caller);
        when(callers.scopeForRecord(eq(caller), eq(45L), any(), any(), any())).thenAnswer(inv -> new EdgeDataScope(45L,
                TunnelActor.pspUser("u-12", "MLRO", 45L, inv.getArgument(3), null), caller));
        when(store.store(any())).thenAnswer(inv -> {
            FiledDocumentStore.NewDocument n = inv.getArgument(0);
            RegulatoryFiledDocument d = new RegulatoryFiledDocument();
            d.setId(900L);
            d.setPspId(n.pspId());
            d.setDocumentKind(n.kind().name());
            d.setWorkflowType(n.workflowType().name());
            d.setWorkflowId(n.workflowId());
            d.setFilingRef(n.filingRef());
            d.setContentSha256("ab".repeat(32));
            d.setContentLength((long) n.content().length());
            d.setTransactionCount(n.data().transactionCount());
            d.setDataSource(n.data().source());
            d.setFiledAt(n.filedAt());
            d.setRetainUntil(n.filedAt().plusSeconds(1));
            return d;
        });
    }

    /** A user of a PSP with a role. */
    static User user(long id, long psp, String role) {
        Role r = new Role();
        r.setName(role);
        Psp p = new Psp();
        p.setPspId(psp);
        User u = new User();
        u.setId(id);
        u.setUsername(role.toLowerCase() + id);
        u.setRole(r);
        u.setPsp(p);
        return u;
    }

    /** An APPROVED SAR of PSP 45 created by user 10, approved by user 11, referencing decision record A. */
    private SuspiciousActivityReport approvedSar() {
        SuspiciousActivityReport sar = new SuspiciousActivityReport();
        sar.setId(77L);
        sar.setSarReference("SAR-2026-0077");
        sar.setPspId(45L);
        sar.setStatus(SarStatus.APPROVED);
        sar.setJurisdiction("KE");
        sar.setNarrative("Structuring");
        sar.setCreatedBy(user(10L, 45L, "INVESTIGATOR"));
        sar.setApprovedBy(user(11L, 45L, "MLRO"));
        sar.setTransactionReferences(List.of(TransactionReference.ofDecisionRecord(A, "AUTH-1", "site-a")));
        return sar;
    }

    /** One filed transaction. */
    private static FilingExtract extract() {
        return new FilingExtract("EDGE", List.of(GoAmlFilingRendererTest.txn(A.toString(), 210_000_000L, "KES")), null,
                List.of(UUID.randomUUID()));
    }

    /** Verifies: a filing pulls, stores the document, marks FILED, holds the transactions and audits. */
    @Test
    void filesAnApprovedSar() {
        SuspiciousActivityReport sar = approvedSar();
        when(sars.findById(77L)).thenReturn(Optional.of(sar));
        when(sars.lockById(77L)).thenReturn(Optional.of(sar));
        when(data.extractSar(any(), eq("SAR"), eq("SAR-2026-0077"), anyList())).thenReturn(extract());

        RegulatoryFilingService.FilingResult r = service.fileSar(77L, " FRC/2026/1 ");

        assertThat(r.status()).isEqualTo("FILED");
        assertThat(r.document().id()).isEqualTo(900L);
        verify(store).store(org.mockito.ArgumentMatchers.argThat(n -> n.content().contains("<tran_amount_original>2100000.00")
                && n.regulatorReference().equals("FRC/2026/1") && n.retentionYears() == 7 && n.filedAt().equals(NOW)));
        verify(workflow).recordFiled(eq(sar), eq("FRC/2026/1"), eq(mlro), any());
        verify(holds).ensureCoverage(45L, LegalHoldWorkflow.Type.SAR, 77L, "SAR", "SAR-2026-0077", List.of(A), "u-12");
        verify(holds).markWorkflowEnded(LegalHoldWorkflow.Type.SAR, 77L, NOW);
        verify(audit).logAction(eq(mlro), eq("FILED_DOCUMENT_STORED"), eq("REGULATORY_FILED_DOCUMENT"), eq("900"), any(),
                any(), any(), any());
        verify(callers).scopeForRecord(any(), eq(45L), any(), eq(TunnelActor.Purpose.SAR_FILING), any());
    }

    /** Verifies: an Edge refusal changes nothing (no document, no state change, no hold) and is retryable. */
    @Test
    void edgeRefusalChangesNothing() {
        SuspiciousActivityReport sar = approvedSar();
        when(sars.findById(77L)).thenReturn(Optional.of(sar));
        when(data.extractSar(any(), any(), any(), anyList()))
                .thenThrow(FilingException.edgeFailure(TunnelError.offline("site-a", NOW), "SAR-2026-0077"));

        assertThatThrownBy(() -> service.fileSar(77L, null)).isInstanceOfSatisfying(FilingException.class,
                e -> assertThat(e.httpStatus()).isEqualTo(503));
        verify(store, never()).store(any());
        verifyNoInteractions(workflow, holds);
        assertThat(sar.getStatus()).isEqualTo(SarStatus.APPROVED);
    }

    /** Verifies: only an independently APPROVED, unfiled SAR can be filed. */
    @Test
    void stateRulesFollowTheSarWorkflow() {
        SuspiciousActivityReport sar = approvedSar();
        when(sars.findById(77L)).thenReturn(Optional.of(sar));
        sar.setStatus(SarStatus.PENDING_REVIEW);
        assertThatThrownBy(() -> service.fileSar(77L, null)).isInstanceOfSatisfying(FilingException.class,
                e -> assertThat(e.code()).isEqualTo(FilingException.Code.FILING_STATE_CONFLICT));
        sar.setStatus(SarStatus.APPROVED);
        sar.setApprovedBy(sar.getCreatedBy());
        assertThatThrownBy(() -> service.fileSar(77L, null)).isInstanceOfSatisfying(FilingException.class,
                e -> assertThat(e.getMessage()).contains("maker-checker"));
        sar.setStatus(SarStatus.FILED);
        assertThatThrownBy(() -> service.fileSar(77L, null)).isInstanceOfSatisfying(FilingException.class,
                e -> assertThat(e.code()).isEqualTo(FilingException.Code.ALREADY_FILED));
        verifyNoInteractions(data);
    }

    /** Verifies: platform staff, other PSPs and non-compliance roles cannot file. */
    @Test
    void onlyTheOwnPspsMlroOrComplianceOfficerFiles() {
        when(sars.findById(77L)).thenReturn(Optional.of(approvedSar()));

        User platform = user(1L, 0L, "PLATFORM_ADMIN");
        when(isolation.getCurrentUser()).thenReturn(platform);
        when(isolation.isPlatformAdministrator(platform)).thenReturn(true);
        assertThatThrownBy(() -> service.fileSar(77L, null)).isInstanceOfSatisfying(FilingException.class,
                e -> assertThat(e.code()).isEqualTo(FilingException.Code.FILING_NOT_ALLOWED));

        when(isolation.getCurrentUser()).thenReturn(user(13L, 46L, "MLRO"));
        assertThatThrownBy(() -> service.fileSar(77L, null)).isInstanceOfSatisfying(FilingException.class,
                e -> assertThat(e.code()).isEqualTo(FilingException.Code.FILING_NOT_FOUND));

        when(isolation.getCurrentUser()).thenReturn(user(14L, 45L, "PSP_USER"));
        assertThatThrownBy(() -> service.fileSar(77L, null)).isInstanceOfSatisfying(FilingException.class,
                e -> assertThat(e.code()).isEqualTo(FilingException.Code.FILING_NOT_ALLOWED));
        verifyNoInteractions(data, store);
    }

    /** Verifies: a CTR deadline from an Edge alert files the aggregate, completes the deadline and holds its ids. */
    @Test
    void filesACtrDeadline() {
        ComplianceDeadline d = new ComplianceDeadline();
        d.setId(31L);
        d.setPspId(45L);
        d.setDeadlineType("CTR_FILING");
        d.setSourceType("ALERT");
        d.setSourceId(501L);
        d.setCompleted(false);
        Alert alert = new Alert();
        alert.setAlertId(501L);
        alert.setPspId(45L);
        alert.setEdgeAssessmentId(A);
        when(deadlines.findById(31L)).thenReturn(Optional.of(d));
        when(deadlines.lockById(31L)).thenReturn(Optional.of(d));
        when(alerts.findById(501L)).thenReturn(Optional.of(alert));
        UUID other = UUID.fromString("5a7b9c1d-3e5f-4a6b-9c8d-7e6f5a4b3c2d");
        FilingExtract.CtrAggregate agg = new FilingExtract.CtrAggregate("CARD_BUSINESS_DAY", A.toString(),
                LocalDate.of(2026, 10, 16), "Africa/Nairobi", "USD", new BigDecimal("16170.00"),
                new BigDecimal("15000.00"), 2, List.of(A.toString(), other.toString()), NOW, null, 14L);
        when(data.extractCtr(any(), eq("CTR-31"), any())).thenReturn(new FilingExtract("EDGE",
                List.of(GoAmlFilingRendererTest.txn(A.toString(), 1_000L, "KES")), agg, List.of()));

        RegulatoryFilingService.FilingResult r = service.fileCtr(31L, null);

        assertThat(r.workflowType()).isEqualTo("CTR_DEADLINE");
        assertThat(d.getCompleted()).isTrue();
        verify(holds).ensureCoverage(45L, LegalHoldWorkflow.Type.CTR_DEADLINE, 31L, "CTR", "CTR-31", List.of(A, other),
                "u-12");
        verify(callers).scopeForRecord(any(), eq(45L), any(), eq(TunnelActor.Purpose.CTR_FILING), any());

        // Below the threshold: refused before anything is written.
        d.setCompleted(false);
        FilingExtract.CtrAggregate low = new FilingExtract.CtrAggregate("CARD_BUSINESS_DAY", A.toString(),
                LocalDate.of(2026, 10, 16), "Africa/Nairobi", "USD", new BigDecimal("100.00"),
                new BigDecimal("15000.00"), 1, List.of(A.toString()), NOW, null, 14L);
        when(documents.existsByWorkflowTypeAndWorkflowId("CTR_DEADLINE", 31L)).thenReturn(false);
        when(data.extractCtr(any(), eq("CTR-31"), any())).thenReturn(new FilingExtract("EDGE", List.of(), low, List.of()));
        assertThatThrownBy(() -> service.fileCtr(31L, null)).isInstanceOfSatisfying(FilingException.class,
                e -> assertThat(e.code()).isEqualTo(FilingException.Code.CTR_BELOW_THRESHOLD));
    }
}
