import {
  Activity,
  ArrowRight,
  Banknote,
  Bell,
  Globe,
  MoreVertical,
  Octagon,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import GlassCard from '../Common/GlassCard'
import RiskBadge from '../Common/RiskBadge'
import { useLiveAlerts } from '../../hooks/useDashboard'
import type { Alert } from '../../types'

function alertIconFor(type: string | undefined): { icon: LucideIcon; bg: string } {
  const t = (type ?? '').toUpperCase()
  if (t.includes('SANCTION')) return { icon: Octagon, bg: '#DC2626' }
  if (t.includes('STRUCTUR')) return { icon: Users, bg: '#F59E0B' }
  if (t.includes('VELOCITY')) return { icon: Activity, bg: '#EAB308' }
  if (t.includes('COUNTRY') || t.includes('GEO')) return { icon: Globe, bg: '#7B2332' }
  if (t.includes('CASH') || t.includes('DEPOSIT')) return { icon: Banknote, bg: '#DC2626' }
  return { icon: Bell, bg: '#64748B' }
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

function statusLabel(status?: string) {
  switch ((status ?? '').toUpperCase()) {
    case 'OPEN':
      return 'New'
    case 'INVESTIGATING':
      return 'Investigating'
    case 'RESOLVED':
      return 'Resolved'
    default:
      return status ?? '—'
  }
}

function statusClass(status?: string) {
  switch ((status ?? '').toUpperCase()) {
    case 'OPEN':
      return 'bg-danger/20 text-danger'
    case 'INVESTIGATING':
      return 'bg-warning/20 text-warning'
    case 'RESOLVED':
      return 'bg-success/20 text-success'
    default:
      return 'bg-burgundy-850/50 text-white/55'
  }
}

export default function LiveAlertQueue() {
  const { data, isLoading, error } = useLiveAlerts(6)
  const alerts = data ?? []

  return (
    <GlassCard padding="sm" glowVariant="charcoal" className="flex h-full min-h-0 flex-col !p-3">
      <div className="mb-1.5 flex items-center justify-between">
        <h3 className="text-xs font-semibold text-white">Live Alert Queue</h3>
        <Link
          to="/alerts"
          className="flex items-center gap-1 text-[10px] font-medium text-gold hover:underline"
        >
          View all alerts <ArrowRight size={12} />
        </Link>
      </div>

      {isLoading ? (
        <div className="space-y-1.5">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-8 animate-pulse rounded bg-glass-skeleton" />
          ))}
        </div>
      ) : error ? (
        <p className="text-xs text-danger">Could not load</p>
      ) : alerts.length === 0 ? (
        <div className="flex flex-1 items-center justify-center py-6 text-[10px] text-glass-muted">
          No live alerts
        </div>
      ) : (
        <div className="-mx-1 min-h-0 flex-1 overflow-x-auto overflow-y-hidden">
          <table className="w-full min-w-[480px] text-left text-[10px]">
            <thead>
              <tr className="text-[9px] font-medium uppercase tracking-wide text-glass-muted">
                <th className="px-1.5 py-1">Alert</th>
                <th className="px-1.5 py-1">Entity</th>
                <th className="px-1.5 py-1">Risk</th>
                <th className="px-1.5 py-1">Age</th>
                <th className="px-1.5 py-1">Status</th>
                <th className="px-1.5 py-1">Owner</th>
                <th className="px-1.5 py-1" />
              </tr>
            </thead>
            <tbody>
              {alerts.map((a: Alert) => {
                const { icon: Icon, bg } = alertIconFor(a.alertType)
                return (
                  <tr
                    key={a.id}
                    className="border-t border-glass-border transition-colors hover:bg-burgundy-850/55"
                  >
                    <td className="px-1.5 py-1.5">
                      <div className="flex items-center gap-1.5">
                        <span
                          className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-md"
                          style={{ backgroundColor: bg }}
                        >
                          <Icon size={12} color="#FFFFFF" />
                        </span>
                        <div className="min-w-0">
                          <p className="truncate font-medium text-white">{a.alertType || 'Alert'}</p>
                          <p className="truncate text-[9px] text-glass-muted">
                            {a.description || `#${a.id}`}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="px-1.5 py-1.5 text-white/70">
                      {a.transactionId ? `Txn #${a.transactionId}` : a.caseId ? `Case #${a.caseId}` : '—'}
                    </td>
                    <td className="px-1.5 py-1.5">
                      <RiskBadge level={a.priority} />
                    </td>
                    <td className="px-1.5 py-1.5 text-glass-muted">{timeAgo(a.createdAt)}</td>
                    <td className="px-1.5 py-1.5">
                      <span
                        className={`inline-flex rounded px-1.5 py-0.5 text-[9px] font-semibold ${statusClass(a.status)}`}
                      >
                        {statusLabel(a.status)}
                      </span>
                    </td>
                    <td className="px-1.5 py-1.5 text-glass-muted">Unassigned</td>
                    <td className="px-1.5 py-1.5 text-glass-muted">
                      <MoreVertical size={12} />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </GlassCard>
  )
}
