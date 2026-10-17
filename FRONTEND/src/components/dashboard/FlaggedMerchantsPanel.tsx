import DashboardPanel from './DashboardPanel'
import DashboardWidgetHeader from './DashboardWidgetHeader'
import { DashboardEmpty, DashboardError, DashboardLoading } from './DashboardState'
import { useDecisionStatsSummary } from '../../features/api/decisionStats'

/**
 * Answers: which merchants drew the most flagged decisions (alert, hold, block) this week? Built
 * from the hourly decision rollup, never from transaction data.
 */
export default function FlaggedMerchantsPanel() {
  const { data, isLoading, error } = useDecisionStatsSummary(7)
  const merchants = data?.topMerchants ?? []

  return (
    <DashboardPanel aria-labelledby="db-flagged-merchants" className="min-h-[200px]">
      <DashboardWidgetHeader
        id="db-flagged-merchants"
        title="Most-flagged merchants"
        description="Flagged decisions, last 7 days"
        actionLabel="Analytics"
        actionTo="/transaction-monitoring/analytics"
      />
      {isLoading && !data ? (
        <DashboardLoading rows={5} />
      ) : error ? (
        <DashboardError message="Decision statistics unavailable." />
      ) : merchants.length === 0 ? (
        <DashboardEmpty message="No flagged decisions this week." />
      ) : (
        <ol className="flex min-h-0 flex-1 flex-col gap-1">
          {merchants.map((m) => (
            <li key={m.merchantId} className="flex items-center justify-between gap-2 text-xs">
              <span className="flex min-w-0 items-center gap-2">
                <span className="w-4 flex-shrink-0 tabular-nums text-[var(--db-text-muted)]">{m.rank}.</span>
                <span className="truncate font-mono text-[var(--db-text)]">{m.merchantId}</span>
              </span>
              <span className="flex-shrink-0 tabular-nums text-[var(--db-text-muted)]">
                <span className="font-semibold text-[var(--db-text)]">{m.flagged.toLocaleString()}</span> / {m.records.toLocaleString()} ({m.flaggedRate.toFixed(1)}%)
              </span>
            </li>
          ))}
        </ol>
      )}
    </DashboardPanel>
  )
}
