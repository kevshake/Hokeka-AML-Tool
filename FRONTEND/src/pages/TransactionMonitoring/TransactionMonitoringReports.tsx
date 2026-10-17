import { useState } from "react";
import { useDecisionStatsSummary } from "../../features/api/decisionStats";

const PERIODS = [7, 30, 90] as const;

/**
 * Monitoring report over a period, from the decision records the Edge reports (counts only: the
 * cloud holds no amounts, so volume and average-amount figures come from the Edge's own exports).
 */
export default function TransactionMonitoringReports() {
  const [days, setDays] = useState<(typeof PERIODS)[number]>(30);
  const { data, isLoading, isError } = useDecisionStatsSummary(days);
  const w = data?.window;

  const total = w?.records ?? 0;
  /** Share of {@code n} in the period, two decimals. */
  const rate = (n: number) => (total > 0 ? ((n / total) * 100).toFixed(2) : "0.00");

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-lg font-semibold text-white">Transaction Monitoring Reports</h3>
        <label className="flex items-center gap-2 text-xs text-glass-muted">
          Period
          <select aria-label="Period" value={days} onChange={(e) => setDays(Number(e.target.value) as (typeof PERIODS)[number])} className="hokeka-field h-8 py-0">
            {PERIODS.map((p) => <option key={p} value={p}>Last {p} days</option>)}
          </select>
        </label>
      </div>

      {isLoading && <p className="text-sm text-glass-muted">Loading report data...</p>}
      {isError && <p className="text-sm text-red-300">Decision statistics could not be loaded.</p>}

      {w && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <StatCard label="Transactions evaluated" value={total.toLocaleString()} />
            <StatCard label="Allowed" value={w.allow.toLocaleString()} color="text-emerald-400" />
            <StatCard label="Blocked" value={w.block.toLocaleString()} color="text-red-400" />
            <StatCard label="Alert / Hold" value={(w.alert + w.hold).toLocaleString()} color="text-amber-400" />
            <StatCard label="Sanctions hits" value={w.sanctionsHit.toLocaleString()} color="text-red-400" />
            <StatCard label="CTR required" value={w.ctrRequired.toLocaleString()} />
            <StatCard label="Fail-closed" value={w.failClosed.toLocaleString()} />
            <StatCard label="Average score" value={w.averageScore.toFixed(1)} />
          </div>

          <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
            <h4 className="mb-3 text-sm font-semibold text-white">Summary statistics</h4>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <Metric label="Block rate" value={`${rate(w.block)}%`} />
              <Metric label="Review rate (alert + hold)" value={`${rate(w.alert + w.hold)}%`} />
              <Metric label="Flag rate" value={`${rate(w.flagged)}%`} />
            </div>
            <p className="mt-3 text-[11px] text-glass-muted">
              Counts come from the Edge's decision records. Amounts are not held in the cloud; export transactions from Live Monitoring for amounts.
            </p>
          </div>
        </>
      )}
    </div>
  );
}

/** One summary figure. */
function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">{label}</p>
      <p className="mt-1 text-lg font-bold text-white">{value}</p>
    </div>
  );
}

/** One headline figure. */
function StatCard({ label, value, color = "text-white" }: { label: string; value: string; color?: string }) {
  return (
    <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
      <p className="mb-1 text-[11px] font-semibold uppercase tracking-wider text-glass-muted">{label}</p>
      <p className={`text-xl font-bold ${color}`}>{value}</p>
    </div>
  );
}
