import { Loader2, RefreshCw } from "lucide-react";
import EdgeOfflineBanner from "../../components/EdgeData/EdgeOfflineBanner";
import EdgeSupportGate from "../../components/EdgeData/EdgeSupportGate";
import { useEdgeData } from "../../hooks/useEdgeData";
import { siteCodeLabel } from "../../lib/edgeData";
import type { EdgeDataStatus, EdgeLedgerHealth } from "../../types";

type Site = EdgeDataStatus["sites"][number];

/** Local date-time, or a dash. */
function when(value: string | undefined | null): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

/** Human size of a byte count. */
function bytes(n: number | undefined): string {
  if (n == null) return "—";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = n;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
}

/** Badge class of a tunnel or ledger state. */
function tone(state: string | undefined): string {
  if (state === "ONLINE" || state === "OK") return "border-emerald-500/40 text-emerald-300";
  if (state === "DEGRADED" || state === "DISABLED") return "border-amber-500/40 text-amber-300";
  return "border-red-500/40 text-red-300";
}

/** One labelled figure. */
function Figure({ label, value }: { label: string; value: string | number }) {
  return (
    <div>
      <dt className="text-[10px] uppercase tracking-wider text-glass-muted">{label}</dt>
      <dd className="mt-0.5 text-xs tabular-nums text-ink">{value}</dd>
    </div>
  );
}

/** Ledger health of a site (counts and timestamps only), or why it could not be read. */
function LedgerHealth({ ledger }: { ledger: EdgeLedgerHealth }) {
  if (!ledger.status) {
    return (
      <p className="text-xs text-amber-300" data-testid="ledger-unavailable">
        Ledger health unavailable: {siteCodeLabel(ledger.state ?? "UNKNOWN")}
      </p>
    );
  }
  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-2 md:grid-cols-4">
      <Figure label="Ledger" value={ledger.status} />
      <Figure label="Schema" value={ledger.schemaVersion ?? "—"} />
      <Figure label="Rows (approx.)" value={ledger.rows?.approxCount?.toLocaleString() ?? "—"} />
      <Figure label="Oldest row" value={when(ledger.rows?.oldestEvaluatedAt)} />
      <Figure label="Outbox backlog" value={ledger.outbox?.backlog?.toLocaleString() ?? "—"} />
      <Figure label="Oldest pending" value={when(ledger.outbox?.oldestPendingAt)} />
      <Figure label="Last shipped" value={when(ledger.outbox?.lastShippedAt)} />
      <Figure label="Failed sends 24h" value={ledger.outbox?.failedAttempts24h ?? "—"} />
      <Figure label="Retention" value={ledger.retention ? `${ledger.retention.years} years` : "—"} />
      <Figure label="Last purge" value={when(ledger.retention?.lastPurgeAt)} />
      <Figure label="Next purge" value={when(ledger.retention?.nextPurgeAt)} />
      <Figure label="Partitions dropped" value={ledger.retention?.lastPurgeDroppedPartitions ?? "—"} />
      <Figure label="Active holds" value={ledger.holds ? `${ledger.holds.active} (${ledger.holds.heldRows} rows)` : "—"} />
      <Figure label="Database size" value={bytes(ledger.storage?.dbSizeBytes)} />
      <Figure label="Disk free" value={bytes(ledger.storage?.diskFreeBytes)} />
      <Figure label="Write failures 24h" value={ledger.writeFailures24h ?? "—"} />
    </dl>
  );
}

/** One Edge Site: tunnel state, its nodes and its ledger health. */
function SiteCard({ site }: { site: Site }) {
  return (
    <article className="hokeka-glass-card space-y-3 rounded-xl p-4" data-testid={`site-${site.siteId}`}>
      <header className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-[10px] uppercase tracking-wider text-glass-muted">Edge Site</p>
          <h4 className="font-mono text-sm font-semibold text-ink">{site.siteId}</h4>
        </div>
        <div className="flex items-center gap-2 text-xs">
          <span className={`rounded border px-2 py-0.5 ${tone(site.tunnel)}`}>Tunnel {site.tunnel.toLowerCase()}</span>
          <span className="text-glass-muted">{site.liveSessions} live session{site.liveSessions === 1 ? "" : "s"}</span>
          <span className="text-glass-muted">Last seen {when(site.lastSeenAt)}</span>
        </div>
      </header>
      <table className="hokeka-table">
        <thead>
          <tr><th>Node</th><th>Status</th><th>Tunnel</th><th>In flight</th><th>Agent</th><th>Last poll</th></tr>
        </thead>
        <tbody>
          {site.nodes.map((node) => (
            <tr key={node.edgeId}>
              <td><span className="text-ink">{node.displayName ?? node.edgeId}</span><br /><span className="font-mono text-[10px] text-glass-muted">{node.edgeId}</span></td>
              <td>{node.status ?? "—"}</td>
              <td className={node.connected ? "text-emerald-300" : "text-red-300"}>{node.connected ? "connected" : "not connected"}</td>
              <td className="tabular-nums">{node.inflight}</td>
              <td>{node.agentVersion ?? "—"}</td>
              <td className="text-xs text-glass-muted">{when(node.lastPolledAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <LedgerHealth ledger={site.ledger} />
    </article>
  );
}

/**
 * Edge Sites of the PSP from {@code GET /edge-data/status}: nodes grouped by site with the tunnel
 * state from the Control Plane registry and each site's ledger health pulled over the tunnel
 * (row range, outbox backlog, retention and last purge, holds, storage).
 */
export default function EdgeSitesPanel() {
  const status = useEdgeData<EdgeDataStatus>({
    key: ["edge-data-status"],
    path: "edge-data/status",
    completeness: () => null,
  });
  const sites = status.data?.sites ?? [];

  return (
    <section className="mt-4 space-y-3" aria-label="Edge Sites">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold text-ink">Edge Sites, tunnel and ledger</h3>
          {status.data && (
            <p className="text-xs text-glass-muted">
              {status.data.tunnelEnabled ? "Tunnel enabled" : "Tunnel disabled on this Control Plane"} · source {status.data.source === "EDGE" ? "Edge" : "legacy cloud records"} · as of {when(status.data.generatedAt)}
            </p>
          )}
        </div>
        <button type="button" onClick={status.refetch} className="flex items-center gap-1 text-xs text-glass-muted hover:text-ink">
          {status.isFetching ? <Loader2 size={13} className="animate-spin" /> : <RefreshCw size={13} />} Refresh
        </button>
      </div>
      <EdgeSupportGate />
      <EdgeOfflineBanner state={status.state} error={status.error} lastSeenAt={status.lastSeenAt} retryAfterMs={status.retryAfterMs} onRetry={status.refetch} />
      {status.state === "loading" && <Loader2 size={20} className="animate-spin text-glass-muted" />}
      {status.state === "ready" && sites.length === 0 && (
        <p className="text-sm text-glass-muted">No activated Edge node reports a site yet.</p>
      )}
      <div className="grid gap-3 xl:grid-cols-2">
        {sites.map((site) => <SiteCard key={site.siteId} site={site} />)}
      </div>
    </section>
  );
}
