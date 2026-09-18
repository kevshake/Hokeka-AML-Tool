import { CheckCircle2 } from 'lucide-react'
import DashboardPanel from '../dashboard/DashboardPanel'
import DashboardWidgetHeader from '../dashboard/DashboardWidgetHeader'
import { DashboardEmpty, DashboardError, DashboardLoading } from '../dashboard/DashboardState'
import { useMonitoringAlerts } from '../../features/api/queries'

function timeAgo(iso?: string) {
  if (!iso) return '—'
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return '—'
  const diffSec = Math.max(1, Math.round((Date.now() - then) / 1000))
  if (diffSec < 60) return `${diffSec}s ago`
  if (diffSec < 3600) return `${Math.round(diffSec / 60)}m ago`
  if (diffSec < 86400) return `${Math.round(diffSec / 3600)}h ago`
  return `${Math.round(diffSec / 86400)}d ago`
}

/** Answers: What rescreening / risk-change events need review? */
export default function MonitoringAlertsPanel() {
  const { data, isLoading, error } = useMonitoringAlerts(0, 8)
  const rows = data?.content ?? []

  return (
    <DashboardPanel aria-labelledby="db-monitoring-alerts" className="min-h-[200px]">
      <DashboardWidgetHeader
        id="db-monitoring-alerts"
        title="Monitoring events"
        description="Rescreening and risk-change alerts"
        trailing={
          data?.totalElements != null ? (
            <span className="db-chip">{data.totalElements}</span>
          ) : null
        }
        actionLabel="Monitoring"
        actionTo="/transaction-monitoring"
      />

      {isLoading && !data ? (
        <DashboardLoading rows={4} />
      ) : error ? (
        <DashboardError message="Monitoring alerts unavailable." />
      ) : rows.length === 0 ? (
        <DashboardEmpty message="No monitoring alerts." />
      ) : (
        <ul className="flex min-h-0 flex-1 flex-col gap-1.5 overflow-y-auto">
          {rows.map((row) => (
            <li
              key={row.alertId}
              className="flex items-start justify-between gap-2 rounded-[var(--db-radius-sm)] border border-[var(--db-border)] bg-[var(--db-surface-muted)] px-2.5 py-2"
            >
              <div className="min-w-0">
                <p className="truncate text-xs font-medium text-[var(--db-text)]">
                  {row.alertType || 'Risk change'}
                </p>
                <p className="text-[11px] text-[var(--db-text-muted)]">
                  {row.merchantId != null ? `Merchant ${row.merchantId}` : '—'}
                  {' · '}
                  {timeAgo(row.createdAt)}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-1">
                <span
                  className={
                    row.alertSeverity === 'HIGH' || row.alertSeverity === 'CRITICAL'
                      ? 'db-chip db-chip--danger'
                      : 'db-chip db-chip--warning'
                  }
                >
                  {row.alertSeverity ?? 'MEDIUM'}
                </span>
                {row.acknowledged ? (
                  <CheckCircle2
                    className="h-3.5 w-3.5 text-[var(--db-success)]"
                    aria-label="Acknowledged"
                  />
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      )}
    </DashboardPanel>
  )
}
