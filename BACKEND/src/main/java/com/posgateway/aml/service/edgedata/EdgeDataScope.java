package com.posgateway.aml.service.edgedata;

import com.posgateway.aml.edge.tunnel.TunnelActor;

/**
 * Whose data one Console call reads and as whom: the PSP (from the principal for PSP users, from
 * the {@code pspId} parameter only for platform staff under support access) and the tunnel actor
 * that goes into the request and both audit rows (contract §7.5, §7.15).
 *
 * @param pspId  PSP whose Edge Sites (or legacy rows) are read
 * @param actor  the actor; {@code actor.pspId() == pspId}
 * @param caller the authenticated caller
 */
public record EdgeDataScope(long pspId, TunnelActor actor, EdgeDataCaller caller) {

    /** True when platform staff read a PSP's data under support access (§9). */
    public boolean supportAccess() {
        return actor.kind() == TunnelActor.Kind.PLATFORM_USER;
    }
}
