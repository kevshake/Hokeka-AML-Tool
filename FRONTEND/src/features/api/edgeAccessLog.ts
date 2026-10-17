import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import { buildQueryString, type QueryValue } from "../../lib/edgeData";
import type { EdgeDataAccessLogPage } from "../../types";

/** Filters of the data access log (BACKEND EdgeDataAccessLogService.Filter). */
export interface AccessLogFilters {
  from?: string;
  to?: string;
  op?: string;
  actorKind?: string;
  supportAccess?: "true" | "false";
  outcome?: string;
}

/** Largest page the server serves (contract section 7.14). */
export const ACCESS_LOG_MAX_PAGE = 200;

/** Start of a {@code YYYY-MM-DD} day in local time as an RFC 3339 instant (inclusive bound). */
export function dayStartIso(day: string | undefined): string | undefined {
  return day ? new Date(`${day}T00:00:00`).toISOString() : undefined;
}

/** Start of the day after {@code day} (exclusive upper bound), so the whole day is included. */
export function dayEndIso(day: string | undefined): string | undefined {
  return day ? new Date(new Date(`${day}T00:00:00`).getTime() + 86_400_000).toISOString() : undefined;
}

/** Server query of the log: filters with day bounds converted to instants. */
export function accessLogQuery(filters: AccessLogFilters, pspId?: number | null): Record<string, QueryValue> {
  return {
    pspId: pspId && pspId > 0 ? pspId : undefined,
    from: dayStartIso(filters.from),
    to: dayEndIso(filters.to),
    op: filters.op,
    actorKind: filters.actorKind,
    supportAccess: filters.supportAccess,
    outcome: filters.outcome,
  };
}

/**
 * One server-side page of the PSP-visible log of every pull of its transaction data. Cloud audit
 * data (no transaction values); platform staff name the PSP.
 */
export function useEdgeAccessLog(filters: AccessLogFilters, page: number, size: number, pspId?: number | null, enabled = true) {
  const query = { ...accessLogQuery(filters, pspId), page, size: Math.min(size, ACCESS_LOG_MAX_PAGE) };
  return useQuery<EdgeDataAccessLogPage>({
    queryKey: ["edge-data-access-log", query],
    queryFn: () => apiClient.get<EdgeDataAccessLogPage>(`edge-data/access-log${buildQueryString(query)}`),
    placeholderData: keepPreviousData,
    enabled,
  });
}
