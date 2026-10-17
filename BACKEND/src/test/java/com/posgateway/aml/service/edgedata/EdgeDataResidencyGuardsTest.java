package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.EdgeDataNoStoreFilter;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Static guards of the R6 proxy (contract §7.14, §7.16): no Spring cache annotation and no cache,
 * Redis or Kafka dependency anywhere on the proxy's classes; the no-store filter's coverage; CSV
 * safety of the streamed exports; and the caller resolver's tenant rules.
 */
class EdgeDataResidencyGuardsTest {

    /** Packages of the R6 proxy and its endpoints. */
    private static final List<String> PROXY_PACKAGES = List.of("com.posgateway.aml.service.edgedata",
            "com.posgateway.aml.controller.transaction", "com.posgateway.aml.controller.edgedata");

    /** Types whose presence as a field would let a payload reach a cache or a queue. */
    private static final List<String> FORBIDDEN_FIELD_TYPES = List.of("org.springframework.cache.CacheManager",
            "org.springframework.cache.Cache", "org.springframework.data.redis.core.RedisTemplate",
            "org.springframework.data.redis.core.StringRedisTemplate", "org.springframework.kafka.core.KafkaTemplate",
            "com.github.benmanes.caffeine.cache.Cache");

    /** Every class of the proxy packages (top-level and nested). */
    private static List<Class<?>> proxyClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            /** Accept interfaces, records and nested types too. */
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        List<Class<?>> out = new ArrayList<>();
        for (String pkg : PROXY_PACKAGES) {
            for (var bd : scanner.findCandidateComponents(pkg)) {
                out.add(Class.forName(bd.getBeanClassName()));
            }
        }
        return out;
    }

    /** Verifies: no proxy class or method carries a Spring cache annotation or holds a cache/queue. */
    @Test
    void proxyNeverCachesOrQueuesPayloads() throws Exception {
        List<Class<?>> classes = proxyClasses();
        assertThat(classes).extracting(Class::getSimpleName)
                .contains("EdgeTransactionProxyService", "TransactionReadService", "EdgeTransactionController",
                        "EdgeDataController", "TransactionExportService", "TransactionDetailsService");
        List<Class<? extends Annotation>> cacheAnnotations = List.of(Cacheable.class, CachePut.class, CacheEvict.class,
                Caching.class, CacheConfig.class);
        for (Class<?> c : classes) {
            for (Class<? extends Annotation> a : cacheAnnotations) {
                assertThat(c.isAnnotationPresent(a)).as("%s on %s", a.getSimpleName(), c.getName()).isFalse();
            }
            for (Method m : c.getDeclaredMethods()) {
                for (Class<? extends Annotation> a : cacheAnnotations) {
                    assertThat(m.isAnnotationPresent(a)).as("%s on %s.%s", a.getSimpleName(), c.getName(), m.getName())
                            .isFalse();
                }
            }
            for (Field f : c.getDeclaredFields()) {
                assertThat(FORBIDDEN_FIELD_TYPES).as("field %s.%s", c.getName(), f.getName())
                        .doesNotContain(f.getType().getName());
            }
        }
    }

    /** Verifies: the no-store filter covers every tunnel-backed path and the detail views. */
    @Test
    void noStoreFilterCoversTheTunnelBackedPaths() throws Exception {
        for (String p : List.of("/transactions", "/transactions/abc", "/transactions:batchGet", "/transactions/export",
                "/transactions/x/related", "/edge-data/status", "/edge-data/access-log", "/edge-data/access-log/export",
                "/alerts/12", "/compliance/sar/4/detail", "/cases/9/timeline", "/cases/9/graph", "/cases/9/network")) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1" + p);
            req.setContextPath("/api/v1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            new EdgeDataNoStoreFilter().doFilter(req, res, new MockFilterChain());
            assertThat(res.getHeader("Cache-Control")).as(p).isEqualTo("no-store");
        }
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/v1/merchants");
        other.setContextPath("/api/v1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        new EdgeDataNoStoreFilter().doFilter(other, res, new MockFilterChain());
        assertThat(res.getHeader("Cache-Control")).isNull();
    }

    /** Verifies: CSV cells are RFC 4180 quoted and formula-looking text is neutralised. */
    @Test
    void csvCellsAreQuotedAndFormulasNeutralised() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecordStreamWriter w = new RecordStreamWriter(out, RecordStreamWriter.Format.CSV,
                List.of("a", "b", "c", "d", "e"), mapper);
        w.start();
        ObjectNode r = mapper.createObjectNode();
        r.put("a", "=HYPERLINK(\"x\")");
        r.put("b", "plain, with comma");
        r.put("c", -42);
        r.putArray("d").add(1).add(2);
        r.put("e", "line\nbreak");
        w.record(r);
        w.finish(mapper.createObjectNode(), false, "");
        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("﻿a,b,c,d,e\r\n\"'=HYPERLINK(\"\"x\"\")\",\"plain, with comma\",-42,1;2,"
                + "\"line\nbreak\"\r\n");
        assertThat(RecordStreamWriter.Format.parse("jsonl")).isEqualTo(RecordStreamWriter.Format.NDJSON);
        assertThatThrownBy(() -> RecordStreamWriter.Format.parse("xlsx")).isInstanceOf(EdgeDataException.class);
    }

    /** Verifies: PSP users are bound to their PSP; platform staff must name one and become support pulls. */
    @Test
    void callerResolverEnforcesTenantRules() {
        PspIsolationService isolation = mock(PspIsolationService.class);
        EdgeDataCallerResolver resolver = new EdgeDataCallerResolver(isolation);
        Role role = new Role();
        role.setName("psp_admin");
        Psp psp = new Psp();
        psp.setPspId(12L);
        User u = User.builder().username("api-key:hka_ab12").email("x").passwordHash("").role(role).psp(psp).build();
        when(isolation.getCurrentUser()).thenReturn(u);
        EdgeDataCaller caller = resolver.current();
        assertThat(caller.userRef()).isEqualTo("api-key:hka_ab12");
        assertThat(caller.role()).isEqualTo("PSP_ADMIN");
        assertThat(caller.pspId()).isEqualTo(12L);

        EdgeDataScope own = resolver.scopeFor(caller, null, "ignored for PSP users",
                TunnelActor.Purpose.CASE_INVESTIGATION, EdgeDataCallerResolver.WorkflowRefs.ofCase(31L));
        assertThat(own.pspId()).isEqualTo(12L);
        assertThat(own.actor().supportReason()).isNull();
        assertThat(own.actor().caseId()).isEqualTo("31");
        assertThatThrownBy(() -> resolver.scopeFor(caller, 13L, null, TunnelActor.Purpose.CASE_INVESTIGATION, null))
                .isInstanceOfSatisfying(EdgeDataException.class,
                        e -> assertThat(e.error().code()).isEqualTo(TunnelErrorCode.TENANT_MISMATCH));
        assertThatThrownBy(() -> resolver.scopeFor(caller, null, null, TunnelActor.Purpose.SUPPORT_ACCESS, null))
                .isInstanceOfSatisfying(EdgeDataException.class,
                        e -> assertThat(e.error().param()).isEqualTo("/purpose"));
        assertThatThrownBy(() -> resolver.scopeForRecord(caller, 99L, null, TunnelActor.Purpose.ALERT_REVIEW, null))
                .isInstanceOfSatisfying(EdgeDataException.class,
                        e -> assertThat(e.error().code()).isEqualTo(TunnelErrorCode.TENANT_MISMATCH));

        EdgeDataCaller staff = new EdgeDataCaller("u-1", "SUPER_ADMIN", null, true);
        assertThatThrownBy(() -> resolver.scopeFor(staff, null, "reason long enough", TunnelActor.Purpose.ALERT_REVIEW,
                null)).isInstanceOfSatisfying(EdgeDataException.class,
                e -> assertThat(e.error().param()).isEqualTo("/pspId"));
        EdgeDataScope support = resolver.scopeForRecord(staff, 99L, "  Ticket HK-77 chargeback dispute  ",
                TunnelActor.Purpose.ALERT_REVIEW, EdgeDataCallerResolver.WorkflowRefs.ofAlert("bad id with spaces"));
        assertThat(support.supportAccess()).isTrue();
        assertThat(support.actor().purpose()).isEqualTo(TunnelActor.Purpose.SUPPORT_ACCESS);
        assertThat(support.actor().supportReason()).isEqualTo("Ticket HK-77 chargeback dispute");
        assertThat(support.actor().alertId()).isNull();
    }
}
