import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../lib/apiClient';
import type {
  EdgeNodeCreatedResponse,
  EdgeNodeDetail,
  EdgeNodeView,
  NodeAction,
} from './types';

/* -------------------------------------------------------------------------- */
/* Query keys                                                                 */
/* -------------------------------------------------------------------------- */

export const edgeKeys = {
  all: ['edge-nodes'] as const,
  list: (pspId?: number | null) => ['edge-nodes', 'list', pspId ?? 'all'] as const,
  detail: (id: number) => ['edge-nodes', 'detail', id] as const,
};

/* -------------------------------------------------------------------------- */
/* Reads                                                                      */
/* -------------------------------------------------------------------------- */

/**
 * Fleet listing. `pspId` is an OPTIONAL filter for platform admins; PSP users are
 * scoped to their own PSP server-side regardless of what is sent (PspIsolationService),
 * so the filter is a convenience, never a security boundary.
 */
export function useEdgeNodes(pspId?: number | null) {
  return useQuery<EdgeNodeView[]>({
    queryKey: edgeKeys.list(pspId),
    queryFn: () =>
      apiClient.get<EdgeNodeView[]>(`edge/nodes${pspId != null ? `?pspId=${pspId}` : ''}`),
  });
}

/**
 * Node detail + recent aggregate metrics. When `poll` is set, the query refetches
 * while the node is still moving toward ACTIVE (PENDING/APPROVED) — used by the
 * setup wizard's "waiting for activation" step. Terminal/active states stop the poll.
 */
export function useEdgeNodeDetail(id: number | null, poll = false) {
  return useQuery<EdgeNodeDetail>({
    queryKey: id != null ? edgeKeys.detail(id) : ['edge-nodes', 'detail', 'none'],
    queryFn: () => apiClient.get<EdgeNodeDetail>(`edge/nodes/${id}`),
    enabled: id != null,
    refetchInterval: poll
      ? (query) => {
          const status = query.state.data?.node?.status;
          return status === 'PENDING' || status === 'APPROVED' ? 4000 : false;
        }
      : false,
  });
}

/* -------------------------------------------------------------------------- */
/* Writes                                                                     */
/* -------------------------------------------------------------------------- */

export interface RequestNodeInput {
  pspId: number;
  displayName: string;
}

/**
 * Request (create) a node. The 201 response carries the one-time enrollment code —
 * the caller MUST keep it in component state; it is never retrievable again.
 */
export function useRequestNode() {
  const qc = useQueryClient();
  return useMutation<EdgeNodeCreatedResponse, unknown, RequestNodeInput>({
    mutationFn: (input) => apiClient.post<EdgeNodeCreatedResponse>('edge/nodes', input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: edgeKeys.all });
    },
  });
}

/** approve / reject / suspend / revoke — all share a shape and invalidate the fleet. */
export function useNodeAction() {
  const qc = useQueryClient();
  return useMutation<EdgeNodeView, unknown, { id: number; action: NodeAction }>({
    mutationFn: ({ id, action }) => apiClient.post<EdgeNodeView>(`edge/nodes/${id}/${action}`),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: edgeKeys.all });
      qc.invalidateQueries({ queryKey: edgeKeys.detail(id) });
    },
  });
}

/* -------------------------------------------------------------------------- */
/* Error surfacing                                                            */
/* -------------------------------------------------------------------------- */

/**
 * apiClient throws the parsed error body verbatim. The edge controller answers with
 * `{ error: "<detail>" }`; other layers (e.g. a @PreAuthorize denial) may answer with
 * `{ message: "..." }`. Prefer whichever carries the human-readable detail.
 */
export function resolveEdgeError(err: unknown): string {
  if (!err) return 'Something went wrong. Please try again.';
  if (typeof err === 'string') return err;
  const e = err as Record<string, unknown>;
  const message = typeof e.message === 'string' ? e.message.trim() : '';
  const error = typeof e.error === 'string' ? e.error.trim() : '';
  return message || error || 'The request could not be completed.';
}
