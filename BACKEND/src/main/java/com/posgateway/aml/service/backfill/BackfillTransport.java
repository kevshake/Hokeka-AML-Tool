package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The tunnel calls a back-fill makes, each addressed to exactly one Edge Site of one PSP and made as
 * the {@code SYSTEM} actor {@code system:backfill} (contract §7.5, §11). Every call writes the cloud
 * {@code edge_data_access_log} row (§7.15) through the gateway. Failures are
 * {@link com.posgateway.aml.edge.tunnel.EdgeDataException} carrying the §7.11 error.
 */
public interface BackfillTransport {

    /** One {@code ledger.backfill} phase; returns the Edge's acknowledgement item. */
    ObjectNode backfill(long pspId, String siteId, ObjectNode params);

    /** {@code ledger.health} of the site (one item). */
    ObjectNode health(long pspId, String siteId);

    /** {@code txn.batchGet} of up to 200 ids on the site. */
    BatchGet batchGet(long pspId, String siteId, List<UUID> edgeAssessmentIds);

    /** {@code txn.get} by {@code txnRef} on the site; empty when the Edge answers {@code NOT_FOUND}. */
    Optional<ObjectNode> getByTxnRef(long pspId, String siteId, String txnRef);

    /** The detail items found and the ids absent from the site. */
    record BatchGet(List<ObjectNode> items, List<String> missing) {}
}
