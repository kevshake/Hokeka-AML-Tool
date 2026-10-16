package com.hokeka.edge.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeEvaluation;
import com.hokeka.edge.EdgeRuleInterpreter;
import com.hokeka.edge.contract.ContractSchemas;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Ledger configuration handling that needs no database: missing settings are named by variable
 * (never by value), an unconfigured ledger fails closed, and the target is logged without
 * credentials.
 */
class LedgerConfigurationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractSchemas schemas =
            ContractSchemas.fromDirectory(Path.of(System.getProperty("contracts.dir", "../docs/contracts")));

    @Test
    void anUnconfiguredLedgerFailsClosedAndNamesTheMissingVariablesOnly() {
        LedgerProperties props = new LedgerProperties();
        props.setPassword("s3cret-value");
        EdgeLedger unconfigured = new EdgeLedger(props, mapper, "acme-eu-1");
        assertEquals(EdgeLedger.State.NOT_CONFIGURED, unconfigured.state());
        assertEquals("not configured: missing EDGE_LEDGER_URL, EDGE_LEDGER_USER", unconfigured.stateReason());
        assertFalse(unconfigured.stateReason().contains("s3cret"));
        assertFalse(unconfigured.initialize());

        EvaluationRecorder recorder = new EvaluationRecorder(unconfigured, schemas, mapper);
        EdgeEvaluation answer = (EdgeEvaluation) EdgeLedgerIntegrationTest.controller(recorder)
                .evaluate(EdgeLedgerIntegrationTest.txn("X-1", false)).getBody();
        assertEquals(EdgeRuleInterpreter.Action.HOLD, answer.action());
        assertEquals(1, recorder.retryBacklog());
        assertEquals(0, recorder.drainRetries(), "nothing drains while the ledger is not configured");
    }

    @Test
    void failClosedFalseKeepsTheEvaluatedDecisionButStillBuffersTheRow() {
        LedgerProperties props = new LedgerProperties();
        props.setFailClosed(false);
        EvaluationRecorder recorder = new EvaluationRecorder(new EdgeLedger(props, mapper, "acme-eu-1"), schemas, mapper);
        EdgeEvaluation answer = (EdgeEvaluation) EdgeLedgerIntegrationTest.controller(recorder)
                .evaluate(EdgeLedgerIntegrationTest.txn("X-2", false)).getBody();
        assertEquals(EdgeRuleInterpreter.Action.ALLOW, answer.action());
        assertEquals(1, recorder.retryBacklog());
    }

    @Test
    void retryBufferIsBoundedAndCountsDrops() {
        LedgerProperties props = new LedgerProperties();
        props.setRetryBufferSize(2);
        EvaluationRecorder recorder = new EvaluationRecorder(new EdgeLedger(props, mapper, "acme-eu-1"), schemas, mapper);
        var controller = EdgeLedgerIntegrationTest.controller(recorder);
        for (int i = 0; i < 3; i++) {
            controller.evaluate(EdgeLedgerIntegrationTest.txn("B-" + i, false));
        }
        assertEquals(2, recorder.retryBacklog());
        assertEquals(1, recorder.retryDropped());
    }

    @Test
    void invalidSchemaNameIsReportedAsNotConfigured() {
        LedgerProperties props = new LedgerProperties();
        props.setUrl("jdbc:postgresql://127.0.0.1:1/x");
        props.setUsername("u");
        props.setPassword("p");
        props.setSchema("bad;drop");
        EdgeLedger ledger = new EdgeLedger(props, mapper, "n");
        assertEquals(EdgeLedger.State.NOT_CONFIGURED, ledger.state());
    }

    @Test
    void anUnreachableDatabaseIsDownNotFatal() {
        LedgerProperties props = new LedgerProperties();
        props.setUrl("jdbc:postgresql://127.0.0.1:1/hokeka_ledger");
        props.setUsername("u");
        props.setPassword("p");
        try (EdgeLedger ledger = new EdgeLedger(props, mapper, "n")) {
            assertEquals(EdgeLedger.State.DOWN, ledger.state());
            assertFalse(ledger.initialize());
            assertEquals(EdgeLedger.State.DOWN, ledger.state());
            assertFalse(ledger.stateReason().contains("p@"));
        }
    }

    @Test
    void redactedTargetNeverShowsCredentials() {
        LedgerProperties p = new LedgerProperties();
        p.setUrl("jdbc:postgresql://user:pw@db.internal:5432/hokeka_ledger?password=pw&ssl=true");
        assertEquals("jdbc:postgresql://db.internal:5432/hokeka_ledger", p.redactedTarget());
        assertEquals("acme-1", p.effectiveSiteId("acme-1"));
        p.setSiteId("acme-eu");
        assertEquals("acme-eu", p.effectiveSiteId("acme-1"));
    }
}
