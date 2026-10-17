package com.posgateway.aml.controller;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.case_management.CaseAssignmentService;
import com.posgateway.aml.service.case_management.CaseAuditService;
import com.posgateway.aml.service.case_management.CaseDecisionService;
import com.posgateway.aml.service.case_management.CaseEscalationService;
import com.posgateway.aml.service.case_management.CaseNetworkService;
import com.posgateway.aml.service.case_management.CasePermissionService;
import com.posgateway.aml.service.case_management.CaseTimelineService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** POST /cases/{id}/escalate must have exactly one, complete handler. */
class CaseEscalationEndpointTest {

    private final ComplianceCaseRepository cases = mock(ComplianceCaseRepository.class);
    private final PspIsolationService isolation = mock(PspIsolationService.class);
    private final CasePermissionService permissions = mock(CasePermissionService.class);
    private final CaseEscalationService escalation = mock(CaseEscalationService.class);
    private final CaseAuditService audit = mock(CaseAuditService.class);
    private final UserRepository users = mock(UserRepository.class);
    private CaseWorkflowController controller;
    private User officer;

    @BeforeEach
    void setUp() {
        controller = new CaseWorkflowController(mock(CaseTimelineService.class), mock(CaseNetworkService.class),
                mock(CaseDecisionService.class), escalation, mock(CaseAssignmentService.class), users,
                permissions, audit, cases, isolation,
                mock(com.posgateway.aml.service.edgedata.CaseTransactionDetailsEnricher.class));
        Role role = new Role();
        role.setName("COMPLIANCE_OFFICER");
        Psp psp = new Psp();
        psp.setPspId(3L);
        officer = User.builder().id(9L).username("officer").email("o@x").passwordHash("").role(role).psp(psp).build();
        when(users.findByUsername("officer")).thenReturn(Optional.of(officer));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(officer, null, officer.getAuthorities()));
        ComplianceCase kase = new ComplianceCase();
        kase.setPspId(3L);
        when(cases.findById(1L)).thenReturn(Optional.of(kase));
        when(cases.findById(2L)).thenReturn(Optional.empty());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void escalatesAndAudits() {
        when(permissions.canAct(1L, officer, "ESCALATE")).thenReturn(true);
        ResponseEntity<Map<String, Object>> r = controller.escalateCase(1L, Map.of("reason", " needs MLRO "));
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).containsEntry("success", true);
        verify(escalation).escalateCase(1L, "needs MLRO", 9L);
        verify(audit).logAction(1L, officer, "ESCALATE", "needs MLRO");
    }

    @Test
    void missingCaseCrossTenantForbiddenRoleAndMissingReasonAreRejected() {
        assertThatThrownBy(() -> controller.escalateCase(2L, Map.of("reason", "x")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Case not found");
        assertThatThrownBy(() -> controller.escalateCase(1L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");

        doThrow(new SecurityException("Cannot access data from another PSP")).when(isolation).validatePspAccess(3L);
        assertThatThrownBy(() -> controller.escalateCase(1L, Map.of("reason", "x")))
                .isInstanceOf(SecurityException.class);

        org.mockito.Mockito.reset(isolation);
        when(permissions.canAct(1L, officer, "ESCALATE")).thenReturn(false);
        assertThat(controller.escalateCase(1L, Map.of("reason", "x")).getStatusCode().value()).isEqualTo(403);
        verify(escalation, never()).escalateCase(anyLong(), any(), any());
    }

    @Test
    void exactlyOneControllerMapsCaseEscalate() throws IOException, ClassNotFoundException {
        List<String> handlers = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/posgateway/aml/controller"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String fqcn = Path.of("src/main/java").relativize(f).toString()
                        .replace('\\', '.').replace('/', '.').replaceAll("\\.java$", "");
                Class<?> c = Class.forName(fqcn);
                if (c.getAnnotation(RestController.class) == null) continue;
                RequestMapping base = c.getAnnotation(RequestMapping.class);
                String prefix = base == null || base.value().length == 0 ? "" : base.value()[0];
                for (Method m : c.getDeclaredMethods()) {
                    PostMapping pm = m.getAnnotation(PostMapping.class);
                    if (pm == null) continue;
                    for (String v : pm.value().length > 0 ? pm.value() : pm.path()) {
                        String full = (prefix + v).replaceAll("\\{[^}]+}", "{}");
                        if (full.equals("/cases/{}/escalate")) handlers.add(c.getSimpleName() + "#" + m.getName());
                    }
                }
            }
        }
        assertThat(handlers).containsExactly("CaseWorkflowController#escalateCase");
    }
}
