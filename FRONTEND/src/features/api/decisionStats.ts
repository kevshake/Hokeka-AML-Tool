import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";

/**
 * Dashboard aggregates built from cloud-held decision data (R11): the hourly decision rollup and
 * the decision records. They never come from transaction pulls and carry no amounts.
 */

/** Decision counts of one window (BACKEND DecisionStatsService.Counts). */
export interface DecisionCounts {
  records: number;
  allow: number;
  alert: number;
  hold: number;
  block: number;
  /** ALERT + HOLD + BLOCK. */
  flagged: number;
  failClosed: number;
  ctrRequired: number;
  sanctionsHit: number;
  averageScore: number;
}

/** One day of the series. */
export interface DecisionDay extends DecisionCounts {
  date: string;
  label: string;
}

/** Merchant ranked by flagged decisions. */
export interface DecisionTopMerchant {
  rank: number;
  merchantId: string;
  records: number;
  flagged: number;
  blocked: number;
  flaggedRate: number;
  averageScore: number;
}

/** GET /decision-stats/summary */
export interface DecisionStatsSummary {
  pspId: number | null;
  days: number;
  generatedAt: string;
  source: "DECISION_RECORDS";
  today: DecisionCounts;
  yesterday: DecisionCounts;
  window: DecisionCounts;
  last24h: DecisionCounts;
  trends: { recordsDelta: number; flaggedDelta: number };
  daily: DecisionDay[];
  scoreBands24h: { low: number; medium: number; high: number; critical: number };
  highScore24h: number;
  topMerchants: DecisionTopMerchant[];
}

/** One decision record (class-D fields only). */
export interface RecentDecisionRecord {
  edgeAssessmentId: string;
  txnRef?: string | null;
  pspId: number;
  siteId?: string | null;
  edgeNodeId?: string | null;
  evaluatedAt?: string | null;
  decision?: string | null;
  score?: number | null;
  merchantId?: string | null;
  ctrRequired?: boolean | null;
  sanctionsHit?: boolean | null;
  failClosedCode?: string | null;
  bundleVersion?: number | null;
}

/** GET /decision-stats/recent */
export interface RecentDecisionRecords {
  pspId: number | null;
  items: RecentDecisionRecord[];
  source: "DECISION_RECORDS";
}

/** Daily decision series, today vs yesterday, score bands and top merchants. */
export function useDecisionStatsSummary(days = 7, pspId?: number | null) {
  const query = new URLSearchParams({ days: String(days) });
  if (pspId) query.set("pspId", String(pspId));
  return useQuery<DecisionStatsSummary>({
    queryKey: ["decision-stats", "summary", days, pspId ?? "scope"],
    queryFn: () => apiClient.get<DecisionStatsSummary>(`decision-stats/summary?${query.toString()}`),
    refetchInterval: 60_000,
  });
}

/** The most recent decision records (replaces "recent transactions"). */
export function useRecentDecisionRecords(limit = 10, pspId?: number | null) {
  const query = new URLSearchParams({ limit: String(limit) });
  if (pspId) query.set("pspId", String(pspId));
  return useQuery<RecentDecisionRecords>({
    queryKey: ["decision-stats", "recent", limit, pspId ?? "scope"],
    queryFn: () => apiClient.get<RecentDecisionRecords>(`decision-stats/recent?${query.toString()}`),
    refetchInterval: 30_000,
  });
}
