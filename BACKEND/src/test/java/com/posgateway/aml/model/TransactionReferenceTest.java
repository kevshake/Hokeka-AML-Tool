package com.posgateway.aml.model;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ID-only transaction reference of workflow records (R8). */
class TransactionReferenceTest {

    @Test
    void txnRefFollowsContractShapeAndRefusesCardNumbers() {
        assertEquals("AUTH-1", TransactionReference.safeTxnRef("AUTH-1"));
        assertNull(TransactionReference.safeTxnRef("4111111111111111"), "Luhn-valid PAN");
        assertNull(TransactionReference.safeTxnRef("4111-1111-1111-1112"), "card-shaped, even if Luhn fails");
        assertNull(TransactionReference.safeTxnRef("has space"));
        assertNull(TransactionReference.safeTxnRef("x".repeat(129)));
        assertEquals("1234567890", TransactionReference.safeTxnRef("1234567890"), "short numeric ids are fine");
        assertNull(new TransactionReference(1L, null, "5500000000000004", " ").txnRef());
        assertNull(new TransactionReference(1L, null, null, " ").siteId());
    }

    @Test
    void sameTransactionPrefersEdgeIdThenLegacyIdThenTxnRef() {
        UUID edge = UUID.randomUUID();
        assertTrue(TransactionReference.ofDecisionRecord(edge, "A", null)
                .sameTransactionAs(new TransactionReference(9L, edge, "B", "s")));
        assertFalse(TransactionReference.ofDecisionRecord(edge, "A", null)
                .sameTransactionAs(TransactionReference.ofDecisionRecord(UUID.randomUUID(), "A", null)));
        assertTrue(TransactionReference.ofLegacyTransaction(9L)
                .sameTransactionAs(new TransactionReference(9L, edge, null, null)));
        assertTrue(new TransactionReference(null, null, "R-1", null)
                .sameTransactionAs(new TransactionReference(null, null, "R-1", null)));
        assertFalse(TransactionReference.ofLegacyTransaction(9L).sameTransactionAs(null));
        assertTrue(new TransactionReference(null, null, null, "s").isEmpty());
    }

    @Test
    void mapCarriesIdsOnly() {
        UUID edge = UUID.randomUUID();
        Map<String, Object> map = new TransactionReference(5L, edge, "AUTH-2", "site-a").toMap();
        assertEquals(Map.of("transactionId", 5L, "edgeAssessmentId", edge.toString(), "txnRef", "AUTH-2",
                "siteId", "site-a"), map);
        assertEquals("AUTH-2", new TransactionReference(5L, edge, "AUTH-2", null).label());
        assertEquals(edge.toString(), TransactionReference.ofDecisionRecord(edge, null, null).label());
        assertEquals("5", TransactionReference.ofLegacyTransaction(5L).label());
    }
}
