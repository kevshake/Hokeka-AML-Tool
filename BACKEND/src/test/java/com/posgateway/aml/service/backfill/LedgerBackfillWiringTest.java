package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.controller.residency.LedgerBackfillController;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.repository.residency.PspEdgeSettingsRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.edgedata.EdgeTunnelAccess;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring wiring of the R15 beans (constructor selection, {@code edge.backfill.*} binding, the worker
 * switch) and the controller's routes and argument mapping.
 */
class LedgerBackfillWiringTest {

    /** Scans the back-fill package. */
    @Configuration
    @ComponentScan(basePackageClasses = LedgerBackfillService.class)
    static class Scan {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(Scan.class)
            .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(PlatformTransactionManager.class, () -> Mockito.mock(PlatformTransactionManager.class))
            .withBean(PspEdgeSettingsRepository.class, () -> Mockito.mock(PspEdgeSettingsRepository.class))
            .withBean(AuditLogService.class, () -> Mockito.mock(AuditLogService.class))
            .withBean(PspRepository.class, () -> Mockito.mock(PspRepository.class))
            .withBean(EdgeNodeRepository.class, () -> Mockito.mock(EdgeNodeRepository.class))
            .withBean(PspIsolationService.class, () -> Mockito.mock(PspIsolationService.class))
            .withBean(EdgeTunnelAccess.class, () -> Mockito.mock(EdgeTunnelAccess.class));

    @Test
    void beansWireAndPropertiesBind() {
        runner.withPropertyValues("edge.backfill.worker-enabled=false", "edge.backfill.batch-bytes=12345",
                "edge.backfill.legacy-time-zone=Africa/Nairobi").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(LedgerBackfillService.class).hasSingleBean(BackfillEngine.class)
                    .hasSingleBean(BackfillCoverageService.class).hasSingleBean(TunnelBackfillTransport.class)
                    .doesNotHaveBean(LedgerBackfillWorker.class);
            assertThat(ctx.getBean(BackfillProperties.class).getBatchBytes()).isEqualTo(12345);
            assertThat(ctx.getBean(BackfillProperties.class).legacyZone().getId()).isEqualTo("Africa/Nairobi");
        });
        runner.run(ctx -> assertThat(ctx).hasSingleBean(LedgerBackfillWorker.class));
    }

    @Test
    void controllerRoutesPassReasonsAndIds() throws Exception {
        LedgerBackfillService service = Mockito.mock(LedgerBackfillService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LedgerBackfillController(service)).build();
        UUID id = UUID.randomUUID();
        Mockito.when(service.start(eq(7L), any())).thenReturn(List.of());
        mvc.perform(post("/psps/7/edge-backfill/runs").contentType("application/json")
                .content("{\"defaultSiteId\":\"site-a\",\"reason\":\"Ticket 42 migrate\"}")).andExpect(status().isAccepted());
        Mockito.verify(service).start(7L, new LedgerBackfillService.StartRequest("site-a", "Ticket 42 migrate"));
        mvc.perform(post("/psps/7/edge-backfill/runs/" + id + "/pause").contentType("application/json")
                .content("{\"reason\":\"Pause for the freeze\"}")).andExpect(status().isOk());
        Mockito.verify(service).pause(7L, id, "Pause for the freeze");
        mvc.perform(post("/psps/7/edge-backfill/runs/" + id + "/resume").contentType("application/json")
                .content("{\"reason\":\"Resume after freeze\"}")).andExpect(status().isOk());
        Mockito.verify(service).resume(7L, id, "Resume after freeze");
        mvc.perform(post("/psps/7/edge-backfill/runs/" + id + "/abort").contentType("application/json")
                .content("{\"reason\":\"Stop the migration\"}")).andExpect(status().isOk());
        Mockito.verify(service).abort(7L, id, "Stop the migration");
        mvc.perform(get("/psps/7/edge-backfill").header("X-Support-Reason", "Ticket 42 report"))
                .andExpect(status().isOk());
        Mockito.verify(service).report(7L, "Ticket 42 report");
        mvc.perform(get("/psps/7/edge-backfill/runs/" + id + "/rejections?page=1&size=50&supportReason=Ticket 42 rows"))
                .andExpect(status().isOk());
        Mockito.verify(service).rejections(7L, id, 1, 50, "Ticket 42 rows");
        mvc.perform(post("/psps/7/edge-backfill/runs/" + id + "/rejections/exclude").contentType("application/json")
                .content("{\"legacyTransactionIds\":[\"txn-1\"],\"reason\":\"Duplicate reference\"}"))
                .andExpect(status().isOk());
        Mockito.verify(service).exclude(7L, id, List.of("txn-1"), "Duplicate reference");
    }
}
