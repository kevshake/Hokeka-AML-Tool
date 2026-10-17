import { useQuery } from "@tanstack/react-query";
import { useEdgeSupport } from "../contexts/edgeSupport";
import {
  EDGE_DATA_GC_TIME_MS,
  EDGE_DATA_QUERY_ROOT,
  EdgeRequestError,
  edgeRequest,
  lastSeenOf,
  stateForEnvelope,
  stateForError,
  type CompletenessFields,
  type EdgeDataState,
  type QueryValue,
} from "../lib/edgeData";
import type { EdgeDataError, EdgeUnavailableSite } from "../types";

/** Options of one live Edge pull. */
export interface UseEdgeDataOptions<T> {
  /** Stable identity of the pull inside the edge-data cache (path and parameters). */
  key: ReadonlyArray<unknown>;
  /** API path under /api/v1, e.g. {@code "transactions"}. */
  path: string;
  method?: "GET" | "POST";
  query?: Record<string, QueryValue>;
  body?: unknown;
  /** False to hold the pull (e.g. no id yet). */
  enabled?: boolean;
  /**
   * Whether the endpoint itself needs a support session for platform staff. Record-scoped workflow
   * views (alert, SAR, case) only need the reason, not a chosen PSP, and still render without one.
   */
  requiresSupportSession?: boolean;
  /** Completeness of a successful answer; defaults to reading the envelope fields. */
  completeness?: (data: T) => CompletenessFields | null | undefined;
}

/** Result of {@link useEdgeData}. */
export interface EdgeDataResult<T> {
  data: T | undefined;
  state: EdgeDataState;
  error: EdgeDataError | undefined;
  unavailableSites: EdgeUnavailableSite[];
  lastSeenAt: string | undefined;
  retryAfterMs: number | undefined;
  isFetching: boolean;
  refetch: () => void;
}

/**
 * Pulls transaction data live from the PSP's Edge through the Control Plane. The result is kept
 * only in React Query memory with a short {@code gcTime}, is never persisted and is never cached by
 * the browser. States: loading, ready, partial (some site did not answer), offline, timeout, busy,
 * tenant-error, support-required (a platform admin must choose a PSP and give a reason) and
 * support-disabled (the PSP did not allow Hokeka support access).
 */
export function useEdgeData<T>(options: UseEdgeDataOptions<T>): EdgeDataResult<T> {
  const support = useEdgeSupport();
  const needsSession = options.requiresSupportSession ?? true;
  const gated = support.isPlatform && needsSession && !support.ready;
  const enabled = (options.enabled ?? true) && !gated;

  const query = useQuery<T, unknown>({
    // The support session is part of the key, so a new session never reuses (or orphans) the
    // results pulled under the previous one.
    queryKey: [EDGE_DATA_QUERY_ROOT, support.params?.pspId ?? "own", support.session?.startedAt ?? 0, ...options.key],
    queryFn: ({ signal }) =>
      edgeRequest<T>(options.path, {
        method: options.method,
        query: options.query,
        body: options.body,
        support: support.params,
        signal,
      }),
    enabled,
    gcTime: EDGE_DATA_GC_TIME_MS,
    staleTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    meta: { persist: false },
  });

  const error = query.error instanceof EdgeRequestError ? query.error.body : undefined;
  const completeness = query.data
    ? (options.completeness ?? ((d: T) => d as unknown as CompletenessFields))(query.data)
    : undefined;
  const unavailableSites = completeness?.unavailableSites ?? [];

  let state: EdgeDataState;
  if (gated) state = "support-required";
  else if (!(options.enabled ?? true)) state = "idle";
  else if (query.isPending) state = "loading";
  else if (query.isError) state = stateForError(query.error);
  else state = stateForEnvelope(completeness);

  return {
    data: query.data,
    state,
    error,
    unavailableSites,
    lastSeenAt: lastSeenOf(error, unavailableSites),
    retryAfterMs: error?.retryAfterMs,
    isFetching: query.isFetching,
    refetch: () => {
      void query.refetch();
    },
  };
}
