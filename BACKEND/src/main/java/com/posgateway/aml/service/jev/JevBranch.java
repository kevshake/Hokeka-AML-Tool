package com.posgateway.aml.service.jev;

/**
 * Band outcome for a Jev decision call. Failures always land in {@link #ESCALATE_HUMAN}.
 */
public enum JevBranch {
    /** Standard analyst queue; deterministic decision stands. */
    ESCALATE_HUMAN,
    /** Ambiguous middle — default routing with Jev features attached. */
    DEFAULT,
    /** Upgrade-only routing / priority escalation. */
    ESCALATE_UP,
    /** Phase-2 low-risk candidate queue (human disposition required). */
    LOW_RISK_CANDIDATE,
    /** DP2 likely false-positive four-eyes queue (never auto-dismiss). */
    LIKELY_FALSE_POSITIVE,
    /** DP3 analyst may propose closure (four-eyes still required). */
    PROPOSE_CLOSURE,
    /** DP4 raise tier / open EDD (upgrade-only). */
    RAISE_RISK_TIER
}
