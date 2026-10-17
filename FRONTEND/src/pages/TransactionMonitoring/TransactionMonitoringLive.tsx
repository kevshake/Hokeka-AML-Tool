import { useState } from "react";
import { Link } from "react-router-dom";
import { Loader2, RefreshCw } from "lucide-react";
import TwBadge from "../../components/Common/TwBadge";
import EdgeOfflineBanner from "../../components/EdgeData/EdgeOfflineBanner";
import EdgeSupportGate from "../../components/EdgeData/EdgeSupportGate";
import EdgeCursorPagination from "../../components/EdgeData/EdgeCursorPagination";
import EdgeExportButton from "../../components/EdgeData/EdgeExportButton";
import EdgeTransactionsTable from "../../components/EdgeData/EdgeTransactionsTable";
import { useUrlTableState } from "../../hooks/useUrlTableState";
import { useCursorPager } from "../../hooks/useCursorPager";
import { useEdgeData } from "../../hooks/useEdgeData";
import { useDecisionStatsSummary, useRecentDecisionRecords } from "../../features/api/decisionStats";
import type { QueryValue } from "../../lib/edgeData";
import type { EdgeDataEnvelope, Transaction } from "../../types";

/** Default search window when no dates are chosen (the server default is also 30 days). */
const DEFAULT_WINDOW_DAYS = 30;
/** Longest window one search may cover (contract section 7.6). */
const MAX_WINDOW_DAYS = 93;
const DECISIONS = ["ALLOW", "ALERT", "HOLD", "BLOCK"] as const;

/** Badge colour of a decision. */
const decisionBadge = (decision: string | null | undefined): "danger" | "warning" | "success" | "default" => {
  if (decision === "BLOCK") return "danger";
  if (decision === "HOLD" || decision === "ALERT") return "warning";
  if (decision === "ALLOW") return "success";
  return "default";
};

/** Start of a {@code YYYY-MM-DD} day in local time, as an RFC 3339 instant. */
function dayStart(day: string): string {
  return new Date(`${day}T00:00:00`).toISOString();
}

/**
 * The fixed search window of a filter set. A cursor is bound to its exact parameters, so the
 * window is captured once (when the filters or {@code generation} change) and reused for every page.
 */
function searchWindow(fromDay: string | undefined, toDay: string | undefined, now: number): { from: string; to: string } {
  const to = toDay ? new Date(new Date(`${toDay}T00:00:00`).getTime() + 86_400_000) : new Date(now);
  const earliest = new Date(to.getTime() - MAX_WINDOW_DAYS * 86_400_000);
  let from = fromDay ? new Date(dayStart(fromDay)) : new Date(to.getTime() - DEFAULT_WINDOW_DAYS * 86_400_000);
  if (from < earliest) from = earliest;
  return { from: from.toISOString(), to: to.toISOString() };
}

/**
 * Live Monitoring: the PSP's transactions pulled live from its Edge (cursor paging, Edge filters,
 * streamed export), next to cloud aggregates built from decision records.
 */
export default function TransactionMonitoringLive() {
  const { state: tableState, setFilter, setPageSize } = useUrlTableState(50);
  const filters = tableState.filters;
  const [generation, setGeneration] = useState(0);
  const filterKey = JSON.stringify({ ...filters, size: tableState.size, generation });
  const pager = useCursorPager(filterKey);
  // Captured once per filter set (filterKey changes with any filter, the page size or Refresh), so
  // every page of one search sends the same window its cursor was minted for.
  const [captured, setCaptured] = useState(() => ({ key: filterKey, range: searchWindow(filters.from, filters.to, Date.now()) }));
  let current = captured;
  if (captured.key !== filterKey) {
    current = { key: filterKey, range: searchWindow(filters.from, filters.to, Date.now()) };
    setCaptured(current);
  }
  const range = current.range;

  const searchQuery: Record<string, QueryValue> = {
    from: range.from,
    to: range.to,
    merchantId: filters.merchantId,
    decisions: filters.decision ? [filters.decision] : undefined,
    txnRef: filters.txnRef,
    minScore: filters.minScore,
    ctrRequired: filters.ctrRequired === "true" ? true : undefined,
    sanctionsHit: filters.sanctionsHit === "true" ? true : undefined,
  };

  const list = useEdgeData<EdgeDataEnvelope<Transaction>>({
    key: ["transactions", searchQuery, tableState.size, pager.cursor ?? null],
    path: "transactions",
    query: { ...searchQuery, size: tableState.size, cursor: pager.cursor },
  });
  const summary = useDecisionStatsSummary(1);
  const recent = useRecentDecisionRecords(10);

  const items = list.data?.items ?? [];
  const today = summary.data?.today;
  const last24h = summary.data?.last24h;

  return (
    <div className="flex flex-col gap-4">
      <span className="hokeka-section-label">Monitoring</span>
      <h3 className="mt-1 font-display text-lg font-semibold tracking-tight text-ink">Live Transaction Monitoring</h3>

      <EdgeSupportGate />

      {today && last24h && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-5" aria-label="Decision statistics">
          {[
            ["Evaluated today", today.records],
            ["Flagged today", today.flagged],
            ["Blocked today", today.block],
            ["Flag rate 24h", last24h.records ? `${((last24h.flagged / last24h.records) * 100).toFixed(1)}%` : "0.0%"],
            ["High score 24h", summary.data?.highScore24h ?? 0],
          ].map(([label, value]) => (
            <div key={String(label)} className="hokeka-glass-card rounded-xl p-4">
              <p className="hokeka-field-label mb-1">{label}</p>
              <p className="text-xl font-bold tabular-nums text-ink">{typeof value === "number" ? value.toLocaleString() : value}</p>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="hokeka-glass-card overflow-hidden rounded-xl lg:col-span-2">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-hairline px-4 py-3">
            <h4 className="text-sm font-semibold text-ink">Transactions (live from the Edge)</h4>
            <div className="flex items-center gap-2">
              {list.isFetching && <Loader2 size={14} className="animate-spin text-glass-muted" />}
              <button
                type="button"
                onClick={() => setGeneration((g) => g + 1)}
                className="flex items-center gap-1 text-xs text-glass-muted hover:text-ink"
              >
                <RefreshCw size={13} /> Refresh
              </button>
              <EdgeExportButton path="transactions/export" query={searchQuery} disabled={list.state === "support-required"} />
            </div>
          </div>

          <div className="grid gap-2 border-b border-hairline px-4 py-3 md:grid-cols-4" aria-label="Transaction filters">
            <label className="block text-xs">
              <span className="hokeka-field-label">Decision</span>
              <select aria-label="Decision" value={filters.decision ?? ""} onChange={(e) => setFilter("decision", e.target.value)} className="hokeka-field h-9 w-full">
                <option value="">All</option>
                {DECISIONS.map((d) => <option key={d} value={d}>{d}</option>)}
              </select>
            </label>
            <label className="block text-xs">
              <span className="hokeka-field-label">Merchant ID</span>
              <input aria-label="Merchant ID" defaultValue={filters.merchantId ?? ""} onBlur={(e) => setFilter("merchantId", e.target.value.trim())} className="hokeka-field h-9 w-full" />
            </label>
            <label className="block text-xs">
              <span className="hokeka-field-label">Transaction reference</span>
              <input aria-label="Transaction reference" defaultValue={filters.txnRef ?? ""} onBlur={(e) => setFilter("txnRef", e.target.value.trim())} className="hokeka-field h-9 w-full" />
            </label>
            <label className="block text-xs">
              <span className="hokeka-field-label">Minimum score</span>
              <input aria-label="Minimum score" type="number" min={0} defaultValue={filters.minScore ?? ""} onBlur={(e) => setFilter("minScore", e.target.value)} className="hokeka-field h-9 w-full" />
            </label>
            <label className="block text-xs">
              <span className="hokeka-field-label">From</span>
              <input aria-label="From" type="date" value={filters.from ?? ""} onChange={(e) => setFilter("from", e.target.value)} className="hokeka-field h-9 w-full" />
            </label>
            <label className="block text-xs">
              <span className="hokeka-field-label">To</span>
              <input aria-label="To" type="date" value={filters.to ?? ""} onChange={(e) => setFilter("to", e.target.value)} className="hokeka-field h-9 w-full" />
            </label>
            <label className="flex items-center gap-2 pt-5 text-xs text-ink-muted">
              <input type="checkbox" checked={filters.ctrRequired === "true"} onChange={(e) => setFilter("ctrRequired", e.target.checked ? "true" : "")} />
              CTR required
            </label>
            <label className="flex items-center gap-2 pt-5 text-xs text-ink-muted">
              <input type="checkbox" checked={filters.sanctionsHit === "true"} onChange={(e) => setFilter("sanctionsHit", e.target.checked ? "true" : "")} />
              Sanctions hit
            </label>
          </div>
          <p className="px-4 pt-2 text-[11px] text-glass-muted">
            Window {new Date(range.from).toLocaleDateString()} – {new Date(range.to).toLocaleDateString()} (up to {MAX_WINDOW_DAYS} days per search).
          </p>

          <div className="space-y-3 p-4">
            <EdgeOfflineBanner
              state={list.state}
              error={list.error}
              lastSeenAt={list.lastSeenAt}
              unavailableSites={list.unavailableSites}
              retryAfterMs={list.retryAfterMs}
              onRetry={list.refetch}
              cloudNote="Decision statistics and recent decision records (held in the cloud) are still shown."
            />
            {list.state === "loading" ? (
              <div className="flex items-center justify-center py-12"><Loader2 size={24} className="animate-spin text-glass-muted" /></div>
            ) : (list.state === "ready" || list.state === "partial") ? (
              <EdgeTransactionsTable items={items} />
            ) : null}
          </div>
          <EdgeCursorPagination
            page={pager.page}
            rowCount={items.length}
            rowsPerPage={tableState.size}
            nextCursor={list.data?.nextCursor}
            onNext={pager.next}
            onPrevious={pager.previous}
            onRowsPerPageChange={setPageSize}
            disabled={list.isFetching}
          />
        </div>

        <div className="hokeka-glass-card rounded-xl p-4">
          <h4 className="mb-1 text-sm font-semibold text-ink">Recent decision records</h4>
          <p className="mb-3 text-[11px] text-glass-muted">Decisions reported by the Edge (no transaction details).</p>
          {recent.isLoading ? (
            <p className="text-sm text-glass-muted">Loading decision records…</p>
          ) : recent.isError ? (
            <p className="text-sm text-red-300">Decision records could not be loaded.</p>
          ) : (recent.data?.items.length ?? 0) > 0 ? (
            <div className="flex flex-col gap-1.5">
              {recent.data?.items.map((record) => (
                <Link
                  key={record.edgeAssessmentId}
                  to={`/records/TRANSACTION/${record.edgeAssessmentId}`}
                  className="rounded-lg border border-hairline bg-burgundy-900/60 p-3 hover:border-gold/50"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate font-mono text-xs text-ink-muted">{record.txnRef || record.edgeAssessmentId}</span>
                    <TwBadge variant={decisionBadge(record.decision)}>{record.decision || "-"}</TwBadge>
                  </div>
                  <p className="mt-0.5 text-xs text-glass-muted">
                    {record.evaluatedAt ? new Date(record.evaluatedAt).toLocaleString() : ""}
                    {record.merchantId ? ` · ${record.merchantId}` : ""}
                    {record.score != null ? ` · score ${record.score}` : ""}
                  </p>
                </Link>
              ))}
            </div>
          ) : (
            <p className="text-sm text-glass-muted">No decision records yet</p>
          )}
        </div>
      </div>
    </div>
  );
}
