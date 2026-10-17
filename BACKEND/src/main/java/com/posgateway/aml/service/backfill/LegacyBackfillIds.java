package com.posgateway.aml.service.backfill;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Identifiers of legacy cloud rows in a back-fill (contract §11 step 2).
 *
 * <ul>
 *   <li>{@code legacyTransactionId}: {@code txn-<transactions.txn_id>} for card / cloud-ingest rows and
 *       {@code mat-<multi_asset_transactions.id>} for multi-asset (banking, e-money / mobile money,
 *       crypto with travel rule) rows. The prefix keeps the two id spaces apart and means the id can
 *       never be all digits, so the Luhn guard on the field never fires on a plain sequence number.</li>
 *   <li>{@code edgeAssessmentId}: the existing cloud assessment id when the legacy row has one (the
 *       R8 / V264 rule), else UUIDv5 under namespace {@link #NAMESPACE} over
 *       {@code "<pspId>:<legacyTransactionId>"}, which is deterministic so re-runs are idempotent.</li>
 * </ul>
 */
public final class LegacyBackfillIds {

    /** Contract §11 namespace for minted ids. */
    public static final UUID NAMESPACE = UUID.fromString("6f1c2a5e-0b7d-5d43-9a8e-1f3c4b2a6d90");

    /** Prefix of a card / cloud-ingest transaction ({@code transactions}). */
    public static final String CARD_PREFIX = "txn-";

    /** Prefix of a multi-asset transaction ({@code multi_asset_transactions}). */
    public static final String MULTI_ASSET_PREFIX = "mat-";

    /** Not instantiable. */
    private LegacyBackfillIds() {
    }

    /** {@code legacyTransactionId} of a {@code transactions} row. */
    public static String cardLegacyId(long txnId) {
        return CARD_PREFIX + txnId;
    }

    /** {@code legacyTransactionId} of a {@code multi_asset_transactions} row. */
    public static String multiAssetLegacyId(long id) {
        return MULTI_ASSET_PREFIX + id;
    }

    /** The legacy id for a source table and primary key. */
    public static String legacyId(LegacySource source, long sourceId) {
        return source == LegacySource.TXN ? cardLegacyId(sourceId) : multiAssetLegacyId(sourceId);
    }

    /** The minted §11 edgeAssessmentId of a legacy row that has no cloud assessment. */
    public static UUID mint(long pspId, String legacyTransactionId) {
        return uuidV5(NAMESPACE, pspId + ":" + legacyTransactionId);
    }

    /** RFC 4122 §4.3 name-based UUID, version 5 (SHA-1). */
    static UUID uuidV5(UUID namespace, String name) {
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by RFC 4122 version 5 and is missing from this JVM", e);
        }
        ByteBuffer ns = ByteBuffer.allocate(16);
        ns.putLong(namespace.getMostSignificantBits());
        ns.putLong(namespace.getLeastSignificantBits());
        sha1.update(ns.array());
        sha1.update(name.getBytes(StandardCharsets.UTF_8));
        byte[] hash = sha1.digest();
        hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
        hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
        ByteBuffer out = ByteBuffer.wrap(hash, 0, 16);
        return new UUID(out.getLong(), out.getLong());
    }
}
