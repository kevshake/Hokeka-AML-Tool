import { Link } from 'react-router-dom'
import DashboardPanel from './DashboardPanel'
import DashboardWidgetHeader from './DashboardWidgetHeader'
import { DashboardEmpty, DashboardError, DashboardLoading } from './DashboardState'
import { useRecentDecisionRecords } from '../../features/api/decisionStats'

/** Colour class of a decision. */
function decisionTone(decision: string | null | undefined): string {
  if (decision === 'BLOCK') return 'text-[var(--danger)]'
  if (decision === 'HOLD' || decision === 'ALERT') return 'text-[var(--warning)]'
  return 'text-[var(--db-text-muted)]'
}

/**
 * Answers: what has the Edge decided most recently? Decision records only (reference, decision,
 * score); the transaction itself opens live from the Edge on its record page.
 */
export default function RecentDecisionsPanel() {
  const { data, isLoading, error } = useRecentDecisionRecords(6)
  const items = data?.items ?? []

  return (
    <DashboardPanel aria-labelledby="db-recent-decisions" className="min-h-[200px]">
      <DashboardWidgetHeader
        id="db-recent-decisions"
        title="Recent decisions"
        description="Latest decision records from your Edge"
        actionLabel="Monitoring"
        actionTo="/transaction-monitoring/live"
      />
      {isLoading && !data ? (
        <DashboardLoading rows={6} />
      ) : error ? (
        <DashboardError message="Decision records unavailable." />
      ) : items.length === 0 ? (
        <DashboardEmpty message="No decision records yet." />
      ) : (
        <ul className="flex min-h-0 flex-1 flex-col gap-1">
          {items.map((record) => (
            <li key={record.edgeAssessmentId} className="flex items-center justify-between gap-2 text-xs">
              <Link
                to={`/records/TRANSACTION/${record.edgeAssessmentId}`}
                className="min-w-0 truncate font-mono text-[var(--db-text)] hover:text-[var(--db-accent)]"
              >
                {record.txnRef || record.edgeAssessmentId}
              </Link>
              <span className="flex flex-shrink-0 items-center gap-2">
                {record.score != null && <span className="tabular-nums text-[var(--db-text-muted)]">{record.score}</span>}
                <span className={`font-semibold ${decisionTone(record.decision)}`}>{record.decision ?? '—'}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </DashboardPanel>
  )
}
