import { useEdgeData } from "../../hooks/useEdgeData";
import type { CaseTimeline, EdgePullStatus } from "../../types";

/**
 * Case views whose transaction details the Control Plane pulls live from the PSP's Edge when the
 * view is requested (R6). They go through {@link useEdgeData} so a platform admin's support reason
 * is sent and the pulled details are kept only briefly in memory.
 */

/** A node of the case network graph; TRANSACTION nodes carry pulled {@code transactionDetails}. */
export interface CaseNetworkNode {
  id: string;
  type: string;
  label: string;
  data?: Record<string, unknown>;
}

/** An edge of the case network graph ({@code LINKED_BY_<type>} for Edge links). */
export interface CaseNetworkEdge {
  from: string;
  to: string;
  type: string;
  label: string;
}

/** GET /cases/{id}/network */
export interface CaseNetworkGraph {
  nodes: CaseNetworkNode[];
  edges: CaseNetworkEdge[];
  transactionDetailsStatus?: EdgePullStatus;
  /** Present when {@code related} link types were requested. */
  relatedStatus?: EdgePullStatus;
}

/** The case timeline with its TRANSACTION events' details pulled live. */
export function useCaseTimeline(caseId: number) {
  return useEdgeData<CaseTimeline>({
    key: ["case-timeline", caseId],
    path: `cases/${caseId}/timeline`,
    enabled: !!caseId,
    requiresSupportSession: false,
    completeness: () => null,
  });
}

/**
 * The case network graph. {@code related} adds the Edge's {@code txn.related} links of the case
 * transactions (e.g. CARD, DEVICE), as TRANSACTION nodes joined by LINKED_BY edges.
 */
export function useCaseNetwork(caseId: number, related: string[] = []) {
  return useEdgeData<CaseNetworkGraph>({
    key: ["case-network", caseId, related],
    path: `cases/${caseId}/network`,
    query: { related: related.length ? related : undefined },
    enabled: !!caseId,
    requiresSupportSession: false,
    completeness: () => null,
  });
}
