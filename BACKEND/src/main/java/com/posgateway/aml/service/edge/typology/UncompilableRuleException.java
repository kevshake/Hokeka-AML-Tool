package com.posgateway.aml.service.edge.typology;

/**
 * A rule cannot run on the Edge. The message is the reason recorded in the bundle's
 * {@code uncompiled} manifest and in {@code edge_bundles.uncompiled_rules}; it never contains
 * transaction data.
 */
public class UncompilableRuleException extends IllegalArgumentException {

    /** @param reason why the rule cannot be compiled to Edge IR */
    public UncompilableRuleException(String reason) {
        super(reason);
    }
}
