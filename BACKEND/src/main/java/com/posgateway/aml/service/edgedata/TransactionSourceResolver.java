package com.posgateway.aml.service.edgedata;

import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import org.springframework.stereotype.Service;

/**
 * Applies the R6 legacy-row rule ({@link TransactionSource}): a PSP with an Edge Node that ever
 * activated is served from its Edge only; a PSP without one is served its legacy cloud rows,
 * flagged. Read on every call (one indexed exists query), never cached, so a PSP switches to the
 * Edge on the request after its first node activates.
 */
@Service
public class TransactionSourceResolver {

    private final EdgeNodeRepository nodes;

    /** Reads the PSP's Edge Nodes. */
    public TransactionSourceResolver(EdgeNodeRepository nodes) {
        this.nodes = nodes;
    }

    /** The single source of every transaction field served for {@code pspId}. */
    public TransactionSource sourceFor(long pspId) {
        return nodes.existsByPspIdAndActivatedAtIsNotNull(pspId) ? TransactionSource.EDGE
                : TransactionSource.CLOUD_LEGACY;
    }
}
