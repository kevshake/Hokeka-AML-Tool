package com.posgateway.aml.entity.edge;

/**
 * Lifecycle of a PSP on-prem edge node, exactly as specified in
 * {@code docs/architecture/edge-channel-contract.md} §7:
 *
 * <pre>
 * PENDING ──approve──&gt; APPROVED ──activate(first boot)──&gt; ACTIVE
 *    │                     │                                 │
 *    └──────reject─────────┴──────────revoke/suspend─────────┘
 *                                     ↓
 *                               REVOKED  (bundle distribution stops immediately)
 * </pre>
 */
public enum EdgeNodeStatus {

    /** PSP requested a node; a single-use enrollment code was issued (24 h TTL). */
    PENDING,

    /** A platform admin authorised it; the node may now complete activation. */
    APPROVED,

    /** The node presented its enrollment code and public keys; keys are pinned. Receives bundles. */
    ACTIVE,

    /** Distribution stops on the next poll. Not terminal, but there is no un-suspend transition —
     *  bringing the node back requires a fresh enrollment. */
    SUSPENDED,

    /** Terminal. Irreversible — re-enrollment with fresh keys is required. */
    REVOKED,

    /** Terminal. The enrollment request was declined before activation. */
    REJECTED;

    /** Only an ACTIVE node may be served a rule bundle or have its metrics accepted. */
    public boolean canReceiveBundles() {
        return this == ACTIVE;
    }

    /** REVOKED / REJECTED are terminal: no transition out of them is ever permitted. */
    public boolean isTerminal() {
        return this == REVOKED || this == REJECTED;
    }
}
