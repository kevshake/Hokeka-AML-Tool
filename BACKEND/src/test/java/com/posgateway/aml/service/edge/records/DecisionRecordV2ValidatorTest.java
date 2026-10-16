package com.posgateway.aml.service.edge.records;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.contracts.ContractFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Java decision record v2 validator against the shared contract fixtures
 * (docs/contracts/tunnel/examples/decision-record-v2--*.json), cross-checked with the JSON Schema
 * itself so the hand-written rules can never drift from the schema.
 */
class DecisionRecordV2ValidatorTest {

    /** Every decision record v2 fixture of the contract. */
    static List<String> recordFixtures() {
        return ContractFixtures.fixtureNames("decision-record-v2--");
    }

    @ParameterizedTest
    @MethodSource("recordFixtures")
    void validatorAgreesWithTheContractFixture(String fileName) {
        JsonNode record = ContractFixtures.fixture(fileName);
        Optional<DecisionRecordV2Validator.Violation> violation = DecisionRecordV2Validator.validateRecord(record);
        Set<String> schemaErrors = ContractFixtures.validate("decision-record-v2.schema.json", record);
        boolean invalid = fileName.endsWith(".invalid.json");
        if (invalid) {
            assertTrue(violation.isPresent(), fileName + " must be rejected");
            assertTrue(ContractFixtures.expectedFailures().has(fileName), fileName + " is listed in expected-failures.json");
        } else {
            assertFalse(violation.isPresent(), fileName + " must be accepted: " + violation);
            assertTrue(schemaErrors.isEmpty(), fileName + " is schema-valid: " + schemaErrors);
        }
    }

    @Test
    void forbiddenMembersAreFlaggedAsForbiddenField() {
        for (String name : List.of("decision-record-v2--amount.invalid.json", "decision-record-v2--country.invalid.json",
                "decision-record-v2--customer-id.invalid.json", "decision-record-v2--free-text-reason.invalid.json",
                "decision-record-v2--pan-hash.invalid.json", "decision-record-v2--pan-as-txnref.invalid.json")) {
            Optional<DecisionRecordV2Validator.Violation> v =
                    DecisionRecordV2Validator.validateRecord(ContractFixtures.fixture(name));
            assertTrue(v.isPresent(), name);
            assertEquals(DecisionRecordV2Validator.Code.FORBIDDEN_FIELD, v.get().code(), name);
            assertFalse(v.get().message().contains("4111"), "a PAN value is never echoed: " + v.get().message());
        }
    }

    @Test
    void structuralViolationsAreSchemaInvalid() {
        for (String name : List.of("decision-record-v2--sanctions-without-entry.invalid.json",
                "decision-record-v2--sub-second-timestamp.invalid.json")) {
            Optional<DecisionRecordV2Validator.Violation> v =
                    DecisionRecordV2Validator.validateRecord(ContractFixtures.fixture(name));
            assertEquals(DecisionRecordV2Validator.Code.SCHEMA_INVALID, v.orElseThrow().code(), name);
        }
    }

    @Test
    void schemaConditionalsAreEnforced() {
        ObjectNode failClosed = (ObjectNode) ContractFixtures.fixture("decision-record-v2--fail-closed.json");
        ObjectNode notHold = failClosed.deepCopy().put("decision", "ALLOW");
        assertRejectedBothWays(notHold);
        ObjectNode withEvaluator = failClosed.deepCopy().put("evaluator", "native");
        assertRejectedBothWays(withEvaluator);
        ObjectNode hashWithoutBundle = failClosed.deepCopy()
                .put("bundleHash", "sha256:" + "a".repeat(64));
        assertRejectedBothWays(hashWithoutBundle);

        ObjectNode alert = (ObjectNode) ContractFixtures.fixture("decision-record-v2--alert.json");
        ObjectNode noHash = alert.deepCopy();
        noHash.remove("bundleHash");
        assertRejectedBothWays(noHash);
        ObjectNode noneEvaluator = alert.deepCopy().put("evaluator", "none");
        assertRejectedBothWays(noneEvaluator);
        ObjectNode entriesWithoutHit = alert.deepCopy();
        ((ArrayNode) entriesWithoutHit.putArray("watchlistEntryIds")).add("ofac-sdn:1");
        assertRejectedBothWays(entriesWithoutHit);
        ObjectNode duplicateRules = alert.deepCopy();
        duplicateRules.putArray("triggeredRuleIds").add(5).add(5);
        assertRejectedBothWays(duplicateRules);
        ObjectNode uppercaseId = alert.deepCopy().put("edgeAssessmentId", "3F0C9A2E-7D41-4B8E-9A51-0C2F6B1D7E44");
        assertRejectedBothWays(uppercaseId);
        ObjectNode tooSlow = alert.deepCopy().put("latencyMs", 600_001);
        assertRejectedBothWays(tooSlow);
        ObjectNode merchantPan = alert.deepCopy().put("merchantId", "4111111111111111");
        assertEquals(DecisionRecordV2Validator.Code.FORBIDDEN_FIELD,
                DecisionRecordV2Validator.validateRecord(merchantPan).orElseThrow().code());
    }

    @Test
    void integralFloatsCountAsIntegersLikeJsonSchema() {
        ObjectNode alert = (ObjectNode) ContractFixtures.fixture("decision-record-v2--alert.json");
        alert.put("score", 45.0);
        assertTrue(DecisionRecordV2Validator.validateRecord(alert).isEmpty());
        assertTrue(ContractFixtures.validate("decision-record-v2.schema.json", alert).isEmpty());
        alert.put("score", 45.5);
        assertRejectedBothWays(alert);
    }

    @Test
    void batchFixtureIsAValidBatchAndEveryRecordPasses() {
        JsonNode batch = ContractFixtures.fixture("decision-record-batch-v2--two-records.json");
        assertTrue(DecisionRecordV2Validator.validateBatch(batch).isEmpty());
        assertTrue(ContractFixtures.validate("decision-record-batch-v2.schema.json", batch).isEmpty());
        batch.get("records").forEach(r -> assertTrue(DecisionRecordV2Validator.validateRecord(r).isEmpty()));
    }

    @Test
    void batchWrapperViolationsAreRejected() {
        ObjectNode batch = (ObjectNode) ContractFixtures.fixture("decision-record-batch-v2--two-records.json");
        assertTrue(DecisionRecordV2Validator.validateBatch(batch.deepCopy().put("v", 1)).isPresent());
        assertTrue(DecisionRecordV2Validator.validateBatch(batch.deepCopy().put("extra", true)).isPresent());
        ObjectNode empty = batch.deepCopy();
        empty.putArray("records");
        assertTrue(DecisionRecordV2Validator.validateBatch(empty).isPresent());
        ObjectNode tooMany = batch.deepCopy();
        ArrayNode records = tooMany.putArray("records");
        for (int i = 0; i < 501; i++) {
            records.add(batch.get("records").get(0));
        }
        assertTrue(DecisionRecordV2Validator.validateBatch(tooMany).isPresent());
        assertFalse(ContractFixtures.validate("decision-record-batch-v2.schema.json", tooMany).isEmpty());
    }

    @Test
    void luhnGuardMatchesTheContractDefinition() {
        assertTrue(DecisionRecordV2Validator.looksLikePan("4111-1111-1111-1111"));
        assertTrue(DecisionRecordV2Validator.looksLikePan("4111 1111 1111 1111"));
        assertFalse(DecisionRecordV2Validator.looksLikePan("4111111111111112"));
        assertFalse(DecisionRecordV2Validator.looksLikePan("AUTH-2026-10-16-000981"));
        assertFalse(DecisionRecordV2Validator.looksLikePan("411111111111"));
    }

    /** Both the Java validator and the JSON Schema must reject the record. */
    private static void assertRejectedBothWays(JsonNode record) {
        assertTrue(DecisionRecordV2Validator.validateRecord(record).isPresent(), "validator rejects " + record);
        assertFalse(ContractFixtures.validate("decision-record-v2.schema.json", record).isEmpty(),
                "schema rejects " + record);
    }
}
