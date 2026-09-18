package com.posgateway.aml.dto.edge;

/**
 * Body of {@code POST /api/v1/edge/admin/nodes} — a PSP (or a platform admin acting for one)
 * requesting a new edge node.
 *
 * @param pspId       owning tenant; ignored for PSP users, who may only ever create nodes for their
 *                    own PSP (the value is sanitised server-side)
 * @param displayName human-readable label shown in the fleet UI
 */
public record EdgeNodeRequestBody(Long pspId, String displayName) {
}
