import { Loader2, ShieldAlert } from "lucide-react";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import GlassCard from "../../components/Common/GlassCard";
import TwPagination from "../../components/Common/TwPagination";
import EdgeExportButton from "../../components/EdgeData/EdgeExportButton";
import { useAuth } from "../../contexts/AuthContext";
import { useAllPsps } from "../../features/api/queries";
import { accessLogQuery, useEdgeAccessLog, type AccessLogFilters } from "../../features/api/edgeAccessLog";
import { useUrlTableState } from "../../hooks/useUrlTableState";
import { isPlatformAdmin, pspOptionId, pspOptionLabel } from "../../lib/userAccess";
import type { EdgeDataAccessLogEntry } from "../../types";

const OPS = ["txn.search", "txn.get", "txn.batchGet", "txn.related", "filing.extract", "export.stream", "ledger.backfill", "ledger.health"];
const ACTORS = [
  { value: "PSP_USER", label: "PSP user" },
  { value: "PLATFORM_USER", label: "Hokeka staff" },
  { value: "SYSTEM", label: "System" },
];
const OUTCOMES = ["OK", "PARTIAL", "EDGE_OFFLINE", "EDGE_TIMEOUT", "EDGE_BUSY", "SUPPORT_ACCESS_DISABLED", "TENANT_MISMATCH", "NOT_FOUND"];

/** Local date-time, or a dash. */
function when(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

/** Who pulled, in words. */
function actorLabel(entry: EdgeDataAccessLogEntry): string {
  const kind = ACTORS.find((a) => a.value === entry.actorKind)?.label ?? entry.actorKind;
  return `${kind} · ${entry.actorUserId}${entry.actorRole ? ` (${entry.actorRole})` : ""}`;
}

/** The workflow record a pull was made for, if any. */
function contextLabel(entry: EdgeDataAccessLogEntry): string {
  if (entry.caseId) return `Case ${entry.caseId}`;
  if (entry.alertId) return `Alert ${entry.alertId}`;
  if (entry.filingRef) return `Filing ${entry.filingRef}`;
  return entry.purpose?.toLowerCase().replace(/_/g, " ") ?? "—";
}

/** Compact text of the logged parameter summary (time range, filters, id counts — never values). */
function paramsText(summary: Record<string, unknown>): string {
  const parts = Object.entries(summary ?? {})
    .filter(([, v]) => v !== null && v !== undefined && v !== "")
    .map(([k, v]) => `${k}: ${Array.isArray(v) ? v.join(", ") : typeof v === "object" ? JSON.stringify(v) : String(v)}`);
  return parts.join(" · ") || "—";
}

/**
 * Data access log: every pull of the PSP's transaction data through the Edge tunnel (who, why, which
 * operation, which sites answered, how many items), as recorded by the Control Plane. PSPs see their
 * own log; Hokeka support pulls are highlighted with their reason. Server-paged, filterable and
 * exportable. It never contains the pulled data itself.
 */
export default function DataAccessLogPage() {
  const { user } = useAuth();
  const platform = isPlatformAdmin(user);
  const psps = useAllPsps();
  const { state, setFilter, setPage, setPageSize } = useUrlTableState(50);
  const filters = state.filters as AccessLogFilters & { pspId?: string };
  const pspId = platform ? Number(filters.pspId) || null : null;
  const log = useEdgeAccessLog(filters, state.page, state.size, pspId, !platform || !!pspId);
  const rows = log.data?.content ?? [];

  return (
    <HokekaPageShell title="Data access log" subtitle="Every read of your transaction data on your Edge, by whom and why" noCard>
      <GlassCard padding="md" glowVariant="teal" static>
        <div className="mb-4 grid gap-3 md:grid-cols-4 xl:grid-cols-7" aria-label="Access log filters">
          {platform && (
            <label className="block text-xs">
              <span className="hokeka-field-label">PSP</span>
              <select aria-label="PSP" value={filters.pspId ?? ""} onChange={(e) => setFilter("pspId", e.target.value)} className="hokeka-field h-9 w-full">
                <option value="">Choose a PSP</option>
                {(psps.data ?? []).filter((p) => pspOptionId(p) > 0).map((p) => (
                  <option key={pspOptionId(p)} value={String(pspOptionId(p))}>{pspOptionLabel(p)}</option>
                ))}
              </select>
            </label>
          )}
          <label className="block text-xs">
            <span className="hokeka-field-label">From</span>
            <input aria-label="From" type="date" value={filters.from ?? ""} onChange={(e) => setFilter("from", e.target.value)} className="hokeka-field h-9 w-full" />
          </label>
          <label className="block text-xs">
            <span className="hokeka-field-label">To</span>
            <input aria-label="To" type="date" value={filters.to ?? ""} onChange={(e) => setFilter("to", e.target.value)} className="hokeka-field h-9 w-full" />
          </label>
          <label className="block text-xs">
            <span className="hokeka-field-label">Operation</span>
            <select aria-label="Operation" value={filters.op ?? ""} onChange={(e) => setFilter("op", e.target.value)} className="hokeka-field h-9 w-full">
              <option value="">All</option>
              {OPS.map((op) => <option key={op} value={op}>{op}</option>)}
            </select>
          </label>
          <label className="block text-xs">
            <span className="hokeka-field-label">Actor</span>
            <select aria-label="Actor" value={filters.actorKind ?? ""} onChange={(e) => setFilter("actorKind", e.target.value)} className="hokeka-field h-9 w-full">
              <option value="">All</option>
              {ACTORS.map((a) => <option key={a.value} value={a.value}>{a.label}</option>)}
            </select>
          </label>
          <label className="block text-xs">
            <span className="hokeka-field-label">Support access</span>
            <select aria-label="Support access" value={filters.supportAccess ?? ""} onChange={(e) => setFilter("supportAccess", e.target.value)} className="hokeka-field h-9 w-full">
              <option value="">All</option>
              <option value="true">Support pulls only</option>
              <option value="false">Exclude support pulls</option>
            </select>
          </label>
          <label className="block text-xs">
            <span className="hokeka-field-label">Outcome</span>
            <select aria-label="Outcome" value={filters.outcome ?? ""} onChange={(e) => setFilter("outcome", e.target.value)} className="hokeka-field h-9 w-full">
              <option value="">All</option>
              {OUTCOMES.map((o) => <option key={o} value={o}>{o}</option>)}
            </select>
          </label>
        </div>

        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <p className="text-xs text-glass-muted">
            {log.data ? `${log.data.totalElements.toLocaleString()} pull${log.data.totalElements === 1 ? "" : "s"}` : ""}
          </p>
          <EdgeExportButton
            path="edge-data/access-log/export"
            query={accessLogQuery(filters, pspId)}
            requiresSupportSession={false}
            disabled={platform && !pspId}
          />
        </div>

        {platform && !pspId ? (
          <p className="py-8 text-center text-sm text-glass-muted">Choose a PSP to read its data access log.</p>
        ) : log.isLoading ? (
          <div className="flex justify-center py-12"><Loader2 size={24} className="animate-spin text-glass-muted" /></div>
        ) : log.isError ? (
          <p className="py-8 text-center text-sm text-red-300">The data access log could not be loaded.</p>
        ) : (
          <div className="hokeka-table-wrap">
            <table className="hokeka-table">
              <thead>
                <tr>
                  <th>Requested</th>
                  <th>Actor</th>
                  <th>Operation</th>
                  <th>For</th>
                  <th>Parameters</th>
                  <th>Sites answered</th>
                  <th>Items</th>
                  <th>Outcome</th>
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 ? (
                  <tr><td colSpan={8} className="px-4 py-8 text-center text-sm text-glass-muted">No pulls match these filters</td></tr>
                ) : rows.map((entry) => (
                  <tr key={entry.auditId} className={entry.supportAccess ? "bg-amber-900/20" : undefined} data-testid={entry.supportAccess ? "support-row" : "access-row"}>
                    <td className="whitespace-nowrap text-xs">{when(entry.requestedAt)}</td>
                    <td className="text-xs">
                      {actorLabel(entry)}
                      {entry.supportAccess && (
                        <p className="mt-1 flex items-start gap-1 text-amber-300">
                          <ShieldAlert size={12} className="mt-0.5 shrink-0" /> Hokeka support access: “{entry.supportReason ?? "no reason recorded"}”
                        </p>
                      )}
                    </td>
                    <td className="font-mono text-xs">{entry.op}</td>
                    <td className="text-xs">{contextLabel(entry)}</td>
                    <td className="max-w-xs text-[11px] text-glass-muted">{paramsText(entry.paramsSummary)}</td>
                    <td className="text-xs">{entry.sitesAnswered?.length ?? 0} / {entry.sitesContacted?.length ?? 0}</td>
                    <td className="tabular-nums text-xs">{entry.itemCount}</td>
                    <td className="text-xs">{entry.outcome ?? "in progress"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {log.data && (
          <TwPagination
            page={state.page}
            totalPages={log.data.totalPages}
            totalCount={log.data.totalElements}
            rowsPerPage={state.size}
            onPageChange={(p) => setPage(p)}
            onRowsPerPageChange={setPageSize}
            rowsPerPageOptions={[25, 50, 100, 200]}
          />
        )}
      </GlassCard>
    </HokekaPageShell>
  );
}
