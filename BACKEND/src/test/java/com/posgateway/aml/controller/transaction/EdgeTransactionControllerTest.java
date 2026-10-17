package com.posgateway.aml.controller.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.EdgeDataNoStoreFilter;
import com.posgateway.aml.dto.common.KeysetPageResponse;
import com.posgateway.aml.edge.tunnel.EdgeDataAccessAuditor;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.EdgeDataGateway;
import com.posgateway.aml.edge.tunnel.EdgeDataRequest;
import com.posgateway.aml.edge.tunnel.EdgeDataResult;
import com.posgateway.aml.edge.tunnel.EdgeDataStream;
import com.posgateway.aml.edge.tunnel.SupportAccessPolicy;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.edge.tunnel.TunnelOp;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.exception.EdgeDataExceptionHandler;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.service.edgedata.EdgeDataCallerResolver;
import com.posgateway.aml.service.edgedata.EdgeTransactionProxyService;
import com.posgateway.aml.service.edgedata.EdgeTunnelAccess;
import com.posgateway.aml.service.edgedata.LegacyTransactionReadService;
import com.posgateway.aml.service.edgedata.TransactionExportService;
import com.posgateway.aml.service.edgedata.TransactionLinksService;
import com.posgateway.aml.service.edgedata.TransactionReadService;
import com.posgateway.aml.service.edgedata.TransactionSourceResolver;
import com.posgateway.aml.service.pagination.KeysetSliceService;
import com.posgateway.aml.service.residency.TransactionReferenceResolver;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R6 tunnel-backed transaction endpoints over the real proxy services with a mocked R4 gateway:
 * tenant isolation (PSP from the principal), the platform support-access path, routing by decision
 * record site, the §7.11 status mapping (offline / timeout / busy / partial), the legacy-row rule,
 * the streamed export and {@code Cache-Control: no-store}.
 */
@WebMvcTest(controllers = EdgeTransactionController.class, properties = {
        "spring.security.enabled=false",
        "spring.data.jpa.repositories.enabled=false"
})
@ContextConfiguration(classes = {EdgeTransactionController.class, TransactionReadService.class,
        EdgeDataCallerResolver.class, TransactionSourceResolver.class, EdgeTransactionProxyService.class,
        LegacyTransactionReadService.class, TransactionExportService.class, EdgeDataNoStoreFilter.class,
        EdgeDataExceptionHandler.class, EdgeTransactionControllerTest.MethodSecurity.class})
class EdgeTransactionControllerTest {

    /** Method security with the application's role hierarchy. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
        /** Same hierarchy as the application (platform roles above ADMIN). */
        @Bean
        RoleHierarchy roleHierarchy() {
            RoleHierarchyImpl h = new RoleHierarchyImpl();
            h.setHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN\nROLE_PLATFORM_ADMIN > ROLE_ADMIN\n"
                    + "ROLE_ADMIN > ROLE_COMPLIANCE_OFFICER\nROLE_ADMIN > ROLE_PSP_ADMIN\nROLE_PSP_ADMIN > ROLE_PSP_USER");
            return h;
        }
    }

    private static final long OWN_PSP = 7L;
    private static final long OTHER_PSP = 8L;
    private static final String ID_A = "0000000a-0000-4000-8000-000000000001";
    private static final String ID_B = "0000000b-0000-4000-8000-000000000002";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean TransactionLinksService links;
    @MockBean PspIsolationService isolation;
    @MockBean EdgeNodeRepository nodes;
    @MockBean AssessmentRepository assessments;
    @MockBean TransactionRepository transactions;
    @MockBean KeysetSliceService keyset;
    @MockBean TransactionReferenceResolver references;
    @MockBean EdgeTunnelAccess tunnel;
    @MockBean EdgeDataGateway gateway;
    @MockBean EdgeDataAccessAuditor auditor;
    @MockBean SupportAccessPolicy supportAccess;

    /** The tunnel beans are present; every PSP has an Edge unless a test says otherwise. */
    @BeforeEach
    void wire() {
        when(tunnel.gateway()).thenReturn(gateway);
        when(tunnel.auditor()).thenReturn(auditor);
        when(tunnel.supportAccess()).thenReturn(supportAccess);
        when(nodes.existsByPspIdAndActivatedAtIsNotNull(anyLong())).thenReturn(true);
    }

    /** Signs in a PSP user of {@link #OWN_PSP}. */
    private void pspUser() {
        Role role = new Role();
        role.setName("PSP_ANALYST");
        Psp psp = new Psp();
        psp.setPspId(OWN_PSP);
        User u = User.builder().id(41L).username("analyst").email("a@x").passwordHash("").role(role).psp(psp).build();
        when(isolation.getCurrentUser()).thenReturn(u);
        when(isolation.isPlatformAdministrator(u)).thenReturn(false);
    }

    /** Signs in Hokeka platform staff. */
    private void platformUser() {
        Role role = new Role();
        role.setName("PLATFORM_ADMIN");
        User u = User.builder().id(1L).username("ops").email("o@x").passwordHash("").role(role).build();
        when(isolation.getCurrentUser()).thenReturn(u);
        when(isolation.isPlatformAdministrator(u)).thenReturn(true);
    }

    /** A txn.search summary item. */
    private static ObjectNode summary(String id, String site) {
        ObjectNode s = JsonNodeFactory.instance.objectNode();
        s.put("edgeAssessmentId", id);
        s.put("txnRef", "ref-" + id.substring(0, 8));
        s.put("siteId", site);
        s.put("evaluatedAt", "2026-10-16T09:00:00.000Z");
        s.put("decision", "ALERT");
        s.put("score", 61);
        s.put("merchantId", "mrc_1");
        s.put("amountMinor", 12500);
        s.put("currency", "KES");
        return s;
    }

    /** A gateway result. */
    private static EdgeDataResult result(TunnelOp op, List<ObjectNode> items, String next,
                                         List<EdgeDataResult.UnavailableSite> unavailable, List<String> missing) {
        return new EdgeDataResult(UUID.randomUUID(), op, items, next, !unavailable.isEmpty(), null, unavailable,
                missing, null, Map.of(), List.of("site-a"));
    }

    /** Verifies: a PSP user's list reads the principal's PSP, with Console aliases, source and no-store. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void pspUserListReadsThePrincipalsPspWithSourceAndNoStore() throws Exception {
        pspUser();
        when(gateway.request(any(EdgeDataRequest.class)))
                .thenReturn(result(TunnelOp.TXN_SEARCH, List.of(summary(ID_A, "site-a")), "cur-1", List.of(), List.of()));
        mvc.perform(get("/transactions").param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.source").value("EDGE"))
                .andExpect(jsonPath("$.items[0].source").value("EDGE"))
                .andExpect(jsonPath("$.items[0].id").value(ID_A))
                .andExpect(jsonPath("$.items[0].amountCents").value(12500))
                .andExpect(jsonPath("$.items[0].txnTs").value("2026-10-16T09:00:00.000Z"))
                .andExpect(jsonPath("$.nextCursor").value("cur-1"))
                .andExpect(jsonPath("$.incomplete").value(false));
        ArgumentCaptor<EdgeDataRequest> req = ArgumentCaptor.forClass(EdgeDataRequest.class);
        verify(gateway).request(req.capture());
        assertThat(req.getValue().pspId()).isEqualTo(OWN_PSP);
        assertThat(req.getValue().op()).isEqualTo(TunnelOp.TXN_SEARCH);
        assertThat(req.getValue().pageLimit()).isEqualTo(25);
        assertThat(req.getValue().actor().kind()).isEqualTo(TunnelActor.Kind.PSP_USER);
        assertThat(req.getValue().actor().pspId()).isEqualTo(OWN_PSP);
        assertThat(req.getValue().actor().purpose()).isEqualTo(TunnelActor.Purpose.TRANSACTION_MONITORING);
        assertThat(req.getValue().params().has("from")).isTrue();
    }

    /** Verifies: a PSP user naming another PSP is refused before anything is pulled. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void pspUserNamingAnotherPspIsRefusedWithoutAPull() throws Exception {
        pspUser();
        mvc.perform(get("/transactions").param("pspId", String.valueOf(OTHER_PSP)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TENANT_MISMATCH"));
        mvc.perform(get("/transactions/" + ID_A).param("pspId", String.valueOf(OTHER_PSP)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_MISMATCH"));
        mvc.perform(post("/transactions:batchGet").with(csrf()).param("pspId", String.valueOf(OTHER_PSP))
                        .contentType("application/json").content("{\"edgeAssessmentIds\":[\"" + ID_A + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_MISMATCH"));
        verifyNoInteractions(gateway);
    }

    /**
     * Verifies: another PSP's decision record id never routes a PSP user's get to that PSP — the
     * record lookup is scoped to the principal's PSP and the pull addresses only the principal's
     * Edges, which do not hold it (404).
     */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void anotherPspsRecordIdIsLookedUpOnlyWithinTheCallersPsp() throws Exception {
        pspUser();
        when(assessments.findFirstByPspIdAndEdgeAssessmentId(OWN_PSP, UUID.fromString(ID_A))).thenReturn(Optional.empty());
        when(gateway.request(any(EdgeDataRequest.class)))
                .thenReturn(result(TunnelOp.TXN_GET, List.of(), null, List.of(), List.of()));
        mvc.perform(get("/transactions/" + ID_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        ArgumentCaptor<EdgeDataRequest> req = ArgumentCaptor.forClass(EdgeDataRequest.class);
        verify(gateway).request(req.capture());
        assertThat(req.getValue().pspId()).isEqualTo(OWN_PSP);
        assertThat(req.getValue().sites()).isNull();
        verify(assessments, never()).findFirstByPspIdAndEdgeAssessmentId(eq(OTHER_PSP), any());
    }

    /** Verifies: a get goes only to the site named in the PSP's decision record and adds the record. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void getRoutesToTheDecisionRecordSiteAndAddsCloudFields() throws Exception {
        pspUser();
        Assessment a = new Assessment();
        a.setId(UUID.randomUUID());
        a.setPspId(OWN_PSP);
        a.setEdgeAssessmentId(UUID.fromString(ID_A));
        a.setSiteId("site-b");
        when(assessments.findFirstByPspIdAndEdgeAssessmentId(OWN_PSP, UUID.fromString(ID_A))).thenReturn(Optional.of(a));
        when(gateway.request(any(EdgeDataRequest.class)))
                .thenReturn(result(TunnelOp.TXN_GET, List.of(summary(ID_A, "site-b")), null, List.of(), List.of()));
        mvc.perform(get("/transactions/" + ID_A))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.source").value("EDGE"))
                .andExpect(jsonPath("$.decisionRecord.siteId").value("site-b"));
        ArgumentCaptor<EdgeDataRequest> req = ArgumentCaptor.forClass(EdgeDataRequest.class);
        verify(gateway).request(req.capture());
        assertThat(req.getValue().sites()).containsExactly("site-b");
        assertThat(req.getValue().params().get("edgeAssessmentId").asText()).isEqualTo(ID_A);
    }

    /** Verifies: a malformed id is INVALID_PARAMS naming the pointer only (the value is never echoed). */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void malformedIdIsInvalidParamsWithoutEchoingIt() throws Exception {
        pspUser();
        MvcResult r = mvc.perform(get("/transactions/4111111111111111x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMS"))
                .andExpect(jsonPath("$.param").value("/id"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("4111111111111111");
        verifyNoInteractions(gateway);
    }

    /** Verifies: offline / timeout / busy map to 503 / 504 / 429 envelopes, never 500. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void tunnelOutcomesMapToContractStatuses() throws Exception {
        pspUser();
        when(gateway.request(any(EdgeDataRequest.class)))
                .thenThrow(new EdgeDataException(TunnelError.offline("site-a", Instant.parse("2026-10-16T08:59:00Z"))))
                .thenThrow(new EdgeDataException(TunnelError.timeout("site-a")))
                .thenThrow(new EdgeDataException(TunnelError.busy("site-a", 16, 1500)));
        mvc.perform(get("/transactions"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("EDGE_OFFLINE"))
                .andExpect(jsonPath("$.lastSeenAt").value("2026-10-16T08:59:00Z"))
                .andExpect(jsonPath("$.retryable").value(true));
        mvc.perform(get("/transactions"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("EDGE_TIMEOUT"));
        mvc.perform(get("/transactions"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.code").value("EDGE_BUSY"));
    }

    /** Verifies: a partial answer is 200 with incomplete and the unavailable site. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void partialAnswerIsAnIncompleteEnvelope() throws Exception {
        pspUser();
        when(gateway.request(any(EdgeDataRequest.class))).thenReturn(result(TunnelOp.TXN_SEARCH,
                List.of(summary(ID_A, "site-a")), "cur", List.of(new EdgeDataResult.UnavailableSite("site-b",
                        TunnelErrorCode.EDGE_OFFLINE, Instant.parse("2026-10-16T08:00:00Z"))), List.of()));
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomplete").value(true))
                .andExpect(jsonPath("$.unavailableSites[0].siteId").value("site-b"))
                .andExpect(jsonPath("$.unavailableSites[0].code").value("EDGE_OFFLINE"))
                .andExpect(jsonPath("$.unavailableSites[0].lastSeenAt").value("2026-10-16T08:00:00Z"));
    }

    /** Verifies: platform staff must name the PSP; their pull is a support-access pull with the reason. */
    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platformPullNeedsAPspAndCarriesTheSupportReason() throws Exception {
        platformUser();
        mvc.perform(get("/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMS"))
                .andExpect(jsonPath("$.param").value("/pspId"));
        when(gateway.request(any(EdgeDataRequest.class)))
                .thenReturn(result(TunnelOp.TXN_SEARCH, List.of(), null, List.of(), List.of()));
        mvc.perform(get("/transactions").param("pspId", "9").header("X-Support-Reason", "Ticket HK-4411 reconciliation"))
                .andExpect(status().isOk());
        ArgumentCaptor<EdgeDataRequest> req = ArgumentCaptor.forClass(EdgeDataRequest.class);
        verify(gateway).request(req.capture());
        assertThat(req.getValue().pspId()).isEqualTo(9L);
        assertThat(req.getValue().actor().kind()).isEqualTo(TunnelActor.Kind.PLATFORM_USER);
        assertThat(req.getValue().actor().purpose()).isEqualTo(TunnelActor.Purpose.SUPPORT_ACCESS);
        assertThat(req.getValue().actor().supportReason()).isEqualTo("Ticket HK-4411 reconciliation");
        assertThat(req.getValue().actor().role()).isEqualTo("PLATFORM_ADMIN");
    }

    /** Verifies: batchGet groups ids by decision-record site, one pull each, answer in request order. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void batchGetPullsPerSiteAndKeepsRequestOrder() throws Exception {
        pspUser();
        Assessment a = new Assessment();
        a.setId(UUID.randomUUID());
        a.setEdgeAssessmentId(UUID.fromString(ID_A));
        a.setSiteId("site-a");
        Assessment b = new Assessment();
        b.setId(UUID.randomUUID());
        b.setEdgeAssessmentId(UUID.fromString(ID_B));
        b.setSiteId("site-b");
        when(assessments.findReferencedByPsp(eq(OWN_PSP), any())).thenReturn(List.of(a, b));
        when(gateway.request(any(EdgeDataRequest.class))).thenAnswer(inv -> {
            EdgeDataRequest r = inv.getArgument(0);
            String site = r.sites().iterator().next();
            return site.equals("site-a")
                    ? result(TunnelOp.TXN_BATCH_GET, List.of(summary(ID_A, "site-a")), null, List.of(), List.of())
                    : result(TunnelOp.TXN_BATCH_GET, List.of(), null, List.of(), List.of(ID_B));
        });
        mvc.perform(post("/transactions:batchGet").with(csrf()).contentType("application/json")
                        .content("{\"edgeAssessmentIds\":[\"" + ID_B + "\",\"" + ID_A + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].edgeAssessmentId").value(ID_A))
                .andExpect(jsonPath("$.missing[0]").value(ID_B))
                .andExpect(jsonPath("$.source").value("EDGE"));
        ArgumentCaptor<EdgeDataRequest> req = ArgumentCaptor.forClass(EdgeDataRequest.class);
        verify(gateway, times(2)).request(req.capture());
        assertThat(req.getAllValues()).extracting(r -> r.sites().iterator().next()).containsExactly("site-b", "site-a");
    }

    /** Verifies: related goes to the anchor's site with the normalised link types. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void relatedGoesToTheAnchorsSite() throws Exception {
        pspUser();
        Assessment a = new Assessment();
        a.setId(UUID.randomUUID());
        a.setEdgeAssessmentId(UUID.fromString(ID_A));
        a.setSiteId("site-a");
        when(assessments.findFirstByPspIdAndEdgeAssessmentId(OWN_PSP, UUID.fromString(ID_A))).thenReturn(Optional.of(a));
        ObjectNode item = JsonNodeFactory.instance.objectNode();
        item.put("link", "DEVICE");
        item.put("hop", 1);
        item.put("viaEdgeAssessmentId", ID_A);
        item.set("transaction", summary(ID_B, "site-a"));
        when(gateway.request(any(EdgeDataRequest.class)))
                .thenReturn(result(TunnelOp.TXN_RELATED, List.of(item), null, List.of(), List.of()));
        mvc.perform(get("/transactions/" + ID_A + "/related").param("linkBy", "device", "card").param("depth", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].link").value("DEVICE"))
                .andExpect(jsonPath("$.items[0].transaction.source").value("EDGE"))
                .andExpect(jsonPath("$.items[0].transaction.id").value(ID_B));
        ArgumentCaptor<EdgeDataRequest> req = ArgumentCaptor.forClass(EdgeDataRequest.class);
        verify(gateway).request(req.capture());
        assertThat(req.getValue().op()).isEqualTo(TunnelOp.TXN_RELATED);
        assertThat(req.getValue().sites()).containsExactly("site-a");
        assertThat(req.getValue().params().get("linkBy").toString()).isEqualTo("[\"DEVICE\",\"CARD\"]");
        assertThat(req.getValue().params().get("depth").asInt()).isEqualTo(2);
        assertThat(req.getValue().actor().purpose()).isEqualTo(TunnelActor.Purpose.NETWORK_ANALYSIS);
    }

    /**
     * Verifies: a PSP without an Edge is served its legacy rows flagged CLOUD_LEGACY (never mixed
     * with Edge data), without card hash or raw message, and the read is audited.
     */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void pspWithoutAnEdgeGetsLegacyRowsFlaggedAndAudited() throws Exception {
        pspUser();
        when(nodes.existsByPspIdAndActivatedAtIsNotNull(OWN_PSP)).thenReturn(false);
        TransactionEntity t = new TransactionEntity();
        t.setTxnId(501L);
        t.setPspId(OWN_PSP);
        t.setPanHash("legacy-pan-hash-value");
        t.setIsoMsg("legacy-iso-message");
        t.setAmountCents(4200L);
        t.setCurrency("KES");
        t.setMerchantId("m-9");
        t.setTxnTs(LocalDateTime.parse("2026-10-15T10:00:00"));
        when(keyset.<TransactionEntity>slice(any(), any(), eq(0), eq(50), any(), any(), any(), any(), any()))
                .thenReturn(KeysetPageResponse.of(List.of(t), 50, 1L, 0, null));
        MvcResult r = mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("CLOUD_LEGACY"))
                .andExpect(jsonPath("$.items[0].source").value("CLOUD_LEGACY"))
                .andExpect(jsonPath("$.items[0].id").value(501))
                .andExpect(jsonPath("$.items[0].amountCents").value(4200))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("legacy-pan-hash-value", "legacy-iso-message");
        verifyNoInteractions(gateway);
        verify(auditor).begin(any(), any(), eq(TunnelOp.TXN_SEARCH), any());
        verify(auditor).complete(any(), eq(Set.of()), eq(Set.of()), eq(1), eq(0L), eq("OK"));
    }

    /** Verifies: a platform read of legacy rows without support access is refused and audited. */
    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platformLegacyReadWithoutSupportAccessIsRefusedAndAudited() throws Exception {
        platformUser();
        when(nodes.existsByPspIdAndActivatedAtIsNotNull(9L)).thenReturn(false);
        when(supportAccess.isSupportAccessEnabled(9L)).thenReturn(false);
        mvc.perform(get("/transactions").param("pspId", "9").param("supportReason", "Ticket HK-4411 reconciliation"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUPPORT_ACCESS_DISABLED"));
        verify(auditor).complete(any(), any(), any(), eq(0), eq(0L), eq("SUPPORT_ACCESS_DISABLED"));
        verifyNoInteractions(keyset);
    }

    /** Verifies: a legacy numeric id of an Edge PSP is translated to its reference id and pulled. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void legacyNumericIdOfAnEdgePspIsPulledByItsReferenceId() throws Exception {
        pspUser();
        when(references.forLegacyTransactionId(OWN_PSP, 501L))
                .thenReturn(new TransactionReference(501L, UUID.fromString(ID_A), null, null));
        when(gateway.request(any(EdgeDataRequest.class)))
                .thenReturn(result(TunnelOp.TXN_GET, List.of(summary(ID_A, "site-a")), null, List.of(), List.of()));
        mvc.perform(get("/transactions/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edgeAssessmentId").value(ID_A))
                .andExpect(jsonPath("$.source").value("EDGE"));
        verify(transactions, never()).findByTxnIdAndPspId(anyLong(), anyLong());
    }

    /** Verifies: the export streams NDJSON items then a summary line, with no-store. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void exportStreamsJsonLinesWithASummary() throws Exception {
        pspUser();
        EdgeDataStream stream = org.mockito.Mockito.mock(EdgeDataStream.class);
        when(stream.hasNext()).thenReturn(true, true, false);
        when(stream.next()).thenReturn(summary(ID_A, "site-a"), summary(ID_B, "site-b"));
        when(stream.unavailableSites()).thenReturn(List.of());
        when(stream.totalRows()).thenReturn(2L);
        when(gateway.openExport(any(EdgeDataRequest.class))).thenReturn(stream);
        MvcResult r = mvc.perform(get("/transactions/export").param("format", "ndjson")
                        .param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-17T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Type", "application/x-ndjson;charset=UTF-8"))
                .andReturn();
        String[] lines = r.getResponse().getContentAsString().split("\n");
        assertThat(lines).hasSize(3);
        assertThat(mapper.readTree(lines[0]).get("edgeAssessmentId").asText()).isEqualTo(ID_A);
        assertThat(mapper.readTree(lines[1]).get("source").asText()).isEqualTo("EDGE");
        assertThat(mapper.readTree(lines[2]).get("@summary").get("rows").asLong()).isEqualTo(2);
        verify(stream).close();
        ArgumentCaptor<EdgeDataRequest> req = ArgumentCaptor.forClass(EdgeDataRequest.class);
        verify(gateway).openExport(req.capture());
        assertThat(req.getValue().actor().purpose()).isEqualTo(TunnelActor.Purpose.DATA_EXPORT);
        assertThat(req.getValue().params().get("filter").get("from").asText()).isEqualTo("2026-10-01T00:00:00.000Z");
    }

    /** Verifies: a CSV export names sites missing at open in headers and ends with the incomplete note. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void csvExportFlagsUnavailableSites() throws Exception {
        pspUser();
        EdgeDataStream stream = org.mockito.Mockito.mock(EdgeDataStream.class);
        when(stream.hasNext()).thenReturn(true, false);
        when(stream.next()).thenReturn(summary(ID_A, "site-a"));
        List<EdgeDataResult.UnavailableSite> down = List.of(new EdgeDataResult.UnavailableSite("site-b",
                TunnelErrorCode.EDGE_OFFLINE, Instant.EPOCH));
        when(stream.unavailableSites()).thenReturn(down);
        when(gateway.openExport(any(EdgeDataRequest.class))).thenReturn(stream);
        MvcResult r = mvc.perform(get("/transactions/export").param("format", "csv").param("columns", "amountMinor"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Edge-Incomplete", "true"))
                .andExpect(header().string("X-Edge-Unavailable-Sites", "site-b:EDGE_OFFLINE"))
                .andReturn();
        String body = r.getResponse().getContentAsString();
        assertThat(body).startsWith("﻿edgeAssessmentId,evaluatedAt,siteId,decision,amountMinor,source\r\n");
        assertThat(body).contains(ID_A + ",2026-10-16T09:00:00.000Z,site-a,ALERT,12500,EDGE\r\n");
        assertThat(body).endsWith("# INCOMPLETE EXPORT: 1 rows; unavailable sites: site-b:EDGE_OFFLINE\r\n");
    }

    /** Verifies: an export no site can serve is a JSON error before any byte of the file. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void exportThatCannotStartIsAJsonError() throws Exception {
        pspUser();
        when(gateway.openExport(any(EdgeDataRequest.class)))
                .thenThrow(new EdgeDataException(TunnelError.offline(null, Instant.EPOCH)));
        mvc.perform(get("/transactions/export"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EDGE_OFFLINE"));
        mvc.perform(get("/transactions/export").param("format", "xlsx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.param").value("/format"));
    }

    /** Verifies: offset paging is refused instead of silently returning page 0 again. */
    @Test
    @WithMockUser(roles = "PSP_ANALYST")
    void offsetPagingIsRefused() throws Exception {
        pspUser();
        mvc.perform(get("/transactions").param("page", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.param").value("/page"));
        verifyNoInteractions(gateway);
    }
}
