import {
  Activity,
  Banknote,
  Bell,
  Globe,
  Octagon,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import DashboardPanel from '../dashboard/DashboardPanel'
import DashboardWidgetHeader from '../dashboard/DashboardWidgetHeader'
import DashboardRiskBadge from '../dashboard/DashboardRiskBadge'
import { DashboardEmpty, DashboardError, DashboardLoading } from '../dashboard/DashboardState'
import { useLiveAlerts } from '../../hooks/useDashboard'
import type { Alert } from '../../types'

function alertIconFor(type: string | undefined): { icon: LucideIcon; className: string } {
  const t = (type ?? '').toUpperCase()
  if (t.includes('SANCTION')) return { icon: Octagon, className: 'bg-[var(--db-danger-soft)] text-[var(--db-danger)]' }
  if (t.includes('STRUCTUR')) return { icon: Users, className: 'bg-[var(--db-warning-soft)] text-[var(--db-warning)]' }
  if (t.includes('VELOCITY')) return { icon: Activity, className: 'bg-[var(--db-warning-soft)] text-[var(--db-warning)]' }
  if (t.includes('COUNTRY') || t.includes('GEO')) return { icon: Globe, className: 'bg-[var(--db-info-soft)] text-[var(--db-info)]' }
  if (t.includes('CASH') || t.includes('DEPOSIT')) return { icon: Banknote, className: 'bg-[var(--db-danger-soft)] text-[var(--db-danger)]' }
  return { icon: Bell, className: 'bg-[#f2f4f7] text-[var(--db-text-secondary)]' }
}

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

function statusChip(status?: string) {
  switch ((status ?? '').toUpperCase()) {
    case 'OPEN':
      return { label: 'New', className: 'db-chip db-chip--danger' }
    case 'INVESTIGATING':
      return { label: 'Investigating', className: 'db-chip db-chip--warning' }
    case 'RESOLVED':
      return { label: 'Resolved', className: 'db-chip db-chip--success' }
    default:
      return { label: status ?? '—', className: 'db-chip' }
  }
}

/** Answers: Which open alerts need attention right now? */
export default function LiveAlertQueue() {
  const { data, isLoading, error } = useLiveAlerts(12)
  const alerts = (data ?? []).slice(0, 8)

  return (
    <DashboardPanel aria-labelledby="db-live-alerts" className="h-full">
      <DashboardWidgetHeader
        id="db-live-alerts"
        title="Live alert queue"
        description="Open alerts ordered by recency"
        actionLabel="View all"
        actionTo="/alerts"
      />

      {isLoading && !data ? (
        <DashboardLoading rows={6} />
      ) : error ? (
        <DashboardError message="Live alerts could not be loaded." />
      ) : alerts.length === 0 ? (
        <DashboardEmpty message="No open alerts in the queue." />
      ) : (
        <div className="-mx-1 min-h-0 flex-1 overflow-auto">
          <table className="db-table min-w-[560px]">
            <thead>
              <tr>
                <th scope="col">Alert</th>
                <th scope="col">Entity</th>
                <th scope="col">Risk</th>
                <th scope="col">Age</th>
                <th scope="col">Status</th>
                <th scope="col">Owner</th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((a: Alert) => {
                const { icon: Icon, className } = alertIconFor(a.alertType)
                const status = statusChip(a.status)
                return (
                  <tr key={a.id}>
                    <td>
                      <div className="flex items-center gap-2">
                        <span
                          className={`flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md ${className}`}
                          aria-hidden
                        >
                          <Icon size={13} />
                        </span>
                        <div className="min-w-0">
                          <p className="truncate font-medium text-[var(--db-text)]">
                            {a.alertType || 'Alert'}
                          </p>
                          <p className="truncate text-[10px] text-[var(--db-text-muted)]">
                            {a.description || `#${a.id}`}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td>
                      {a.transactionId ? (
                        <Link className="db-link" to={`/records/TRANSACTION/${a.transactionId}`}>
                          Txn #{a.transactionId}
                        </Link>
                      ) : a.caseId ? (
                        <Link className="db-link" to={`/cases/all?caseId=${a.caseId}`}>
                          Case #{a.caseId}
                        </Link>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td>
                      <DashboardRiskBadge level={a.priority} />
                    </td>
                    <td className="whitespace-nowrap text-[var(--db-text-muted)]">{timeAgo(a.createdAt)}</td>
                    <td>
                      <span className={status.className}>{status.label}</span>
                    </td>
                    <td className="text-[var(--db-text-muted)]">
                      {a.investigator?.trim() || 'Unassigned'}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </DashboardPanel>
  )
}
