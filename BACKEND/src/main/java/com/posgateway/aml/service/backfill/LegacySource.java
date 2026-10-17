package com.posgateway.aml.service.backfill;

/**
 * The legacy cloud transaction stores a back-fill reads (contract §11; plan R15).
 *
 * <ul>
 *   <li>{@link #TXN}: {@code transactions} with its {@code transaction_features} row and the
 *       transaction's cloud assessment (cloud ingest / card rails).</li>
 *   <li>{@link #MAT}: {@code multi_asset_transactions} with the dependent stores that only make sense
 *       with it: {@code mobile_money_transaction_contexts} (e-money / mobile money) and
 *       {@code travel_rule_transfers} (virtual assets). Their fields travel as features of the one
 *       multi-asset row; they have no id of their own on the Edge.</li>
 * </ul>
 *
 * Rows are sent source by source, {@code TXN} first, each ordered by (evaluation time, id).
 */
public enum LegacySource {
    TXN,
    MAT;

    /** The source after this one in send order, or {@code null} after the last. */
    public LegacySource next() {
        return this == TXN ? MAT : null;
    }
}
