package com.posgateway.aml.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.posgateway.aml.util.PanHashGuard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * How a cloud workflow record (alert, case, SAR, screening activity, finding) points at a
 * transaction: by ID only (residency contract invariant I4, plan work package R8). The transaction's
 * details live on the PSP's Edge and are pulled live when viewed (R6); nothing here is transaction
 * data.
 *
 * @param legacyTransactionId the cloud {@code transactions.txn_id} for rows from legacy cloud ingest
 *                            (null for Edge decision records); kept until R12 for legacy ingest and
 *                            the R15 back-fill, never a foreign key
 * @param edgeAssessmentId    the decision record id. For legacy rows this is the cloud assessment id
 *                            that contract section 11 makes the Edge id at back-fill time
 * @param txnRef              the PSP's opaque transaction id (max 128 printable ASCII, never card-shaped)
 * @param siteId              the Edge Site that holds the transaction, when known
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionReference(Long legacyTransactionId, UUID edgeAssessmentId, String txnRef, String siteId) {

    /** Contract 2.5 shape: 1..128 printable ASCII characters, no spaces. */
    private static final Pattern TXN_REF_SHAPE = Pattern.compile("[\\x21-\\x7E]{1,128}");

    /** Normalises the reference: an unsafe txnRef is dropped rather than stored. */
    public TransactionReference {
        txnRef = safeTxnRef(txnRef);
        siteId = siteId == null || siteId.isBlank() ? null : siteId;
    }

    /** A reference to an Edge decision record. */
    public static TransactionReference ofDecisionRecord(UUID edgeAssessmentId, String txnRef, String siteId) {
        return new TransactionReference(null, edgeAssessmentId, txnRef, siteId);
    }

    /** A reference to a legacy cloud-ingested transaction with no known decision record. */
    public static TransactionReference ofLegacyTransaction(Long legacyTransactionId) {
        return new TransactionReference(legacyTransactionId, null, null, null);
    }

    /** True when the reference identifies nothing (no id of any kind). */
    public boolean isEmpty() {
        return legacyTransactionId == null && edgeAssessmentId == null && txnRef == null;
    }

    /** True when both references name the same transaction (same Edge id, else same legacy id, else same txnRef). */
    public boolean sameTransactionAs(TransactionReference other) {
        if (other == null) {
            return false;
        }
        if (edgeAssessmentId != null && other.edgeAssessmentId != null) {
            return edgeAssessmentId.equals(other.edgeAssessmentId);
        }
        if (legacyTransactionId != null && other.legacyTransactionId != null) {
            return legacyTransactionId.equals(other.legacyTransactionId);
        }
        return txnRef != null && txnRef.equals(other.txnRef);
    }

    /** The reference as a JSON-ready map with only the ids that are set (stable key order). */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (legacyTransactionId != null) {
            map.put("transactionId", legacyTransactionId);
        }
        if (edgeAssessmentId != null) {
            map.put("edgeAssessmentId", edgeAssessmentId.toString());
        }
        if (txnRef != null) {
            map.put("txnRef", txnRef);
        }
        if (siteId != null) {
            map.put("siteId", siteId);
        }
        return map;
    }

    /** A short operator label: txnRef, else the Edge id, else the legacy id. */
    public String label() {
        if (txnRef != null) {
            return txnRef;
        }
        if (edgeAssessmentId != null) {
            return edgeAssessmentId.toString();
        }
        return legacyTransactionId != null ? String.valueOf(legacyTransactionId) : "";
    }

    /**
     * Returns {@code candidate} when it is a valid txnRef (contract 2.5), else null. Card-shaped values
     * (13..19 digits once spaces/dashes are removed) are refused whether or not they pass Luhn.
     */
    public static String safeTxnRef(String candidate) {
        if (candidate == null || !TXN_REF_SHAPE.matcher(candidate).matches()
                || PanHashGuard.looksLikeRawPan(candidate)) {
            return null;
        }
        return candidate;
    }
}
