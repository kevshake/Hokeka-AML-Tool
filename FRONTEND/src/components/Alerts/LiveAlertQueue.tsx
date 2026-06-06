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
import { useLiveAlerts } from '../../hooks/useDashboard'
import { cn } from '../../lib/utils'
import type { Alert } from '../../types'

function alertIconFor(type: string | undefined): { icon: LucideIcon; bg: string } {
  const t = (type ?? '').toUpperCase()
  if (t.includes('SANCTION')) return { icon: Octagon, bg: '#DC2626' }
  if (t.includes('STRUCTUR')) return { icon: Users, bg: '#F59E0B' }
  if (t.includes('VELOCITY')) return { icon: Activity, bg: '#EAB308' }
  if (t.includes('COUNTRY') || t.includes('GEO')) return { icon: Globe, bg: '#1F6FEB' }
  if (t.includes('CASH') || t.includes('DEPOSIT')) return { icon: Banknote, bg: '#DC2626' }
  return { icon: Bell, bg: '#64748B' }
}

function riskBadgeClass(priority: string | undefined) {
  switch ((priority ?? '').toUpperCase()) {
    case 'CRITICAL':
      return 'bg-red-100 text-red-700'
    case 'HIGH':
      return 'bg-red-50 text-red-600'
    case 'MEDIUM':
      return 'bg-amber-100 text-amber-700'
    case 'LOW':
      return 'bg-green-100 text-green-700'
    default:
      return 'bg-slate-100 text-slate-600'
  }
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

export default function LiveAlertQueue() {
  const { data, isLoading, error } = useLiveAlerts(6)
  const alerts = data ?? []

  return (
    <div className="flex h-full flex-col rounded-2xl border border-hokeka-border bg-hokeka-card p-6">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-base font-semibold text-slate-900">Live Alert Queue</h3>
        <Link
          to="/alerts"
          className="flex items-center gap-1 text-sm font-medium text-hokeka-secondary hover:underline"
        >
          View all alerts <ArrowRight size={14} />
        </Link>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-12 animate-pulse rounded bg-slate-100" />
          ))}
        </div>
      ) : error ? (
        <p className="text-sm text-hokeka-critical">Could not load</p>
      ) : alerts.length === 0 ? (
        <div className="flex flex-1 items-center justify-center py-16 text-sm text-slate-400">
          No live alerts
        </div>
      ) : (
        <div className="-mx-2 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="text-xs font-medium uppercase tracking-wide text-slate-400">
                <th className="px-2 py-2">Alert</th>
                <th className="px-2 py-2">Customer / Merchant</th>
                <th className="px-2 py-2">Risk</th>
                <th className="px-2 py-2">Time</th>
                <th className="px-2 py-2" />
              </tr>
            </thead>
            <tbody>
              {alerts.map((a: Alert) => {
                const { icon: Icon, bg } = alertIconFor(a.alertType)
                return (
                  <tr
                    key={a.id}
                    className="border-t border-hokeka-border transition-colors hover:bg-slate-50"
                  >
                    <td className="px-2 py-3">
                      <div className="flex items-center gap-3">
                        <span
                          className="flex h-8 w-8 items-center justify-center rounded-lg"
                          style={{ backgroundColor: bg }}
                        >
                          <Icon size={16} color="#FFFFFF" />
                        </span>
                        <div>
                          <p className="font-medium text-slate-900">
                            {a.alertType || 'Alert'}
                          </p>
                          <p className="truncate text-xs text-slate-500">
                            {a.description || `#${a.id}`}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="px-2 py-3 text-slate-600">
                      {a.transactionId ? `Txn #${a.transactionId}` : a.caseId ? `Case #${a.caseId}` : '—'}
                    </td>
                    <td className="px-2 py-3">
                      <span
                        className={cn(
                          'rounded-full px-2.5 py-1 text-xs font-semibold',
                          riskBadgeClass(a.priority)
                        )}
                      >
                        {a.priority ?? '—'}
                      </span>
                    </td>
                    <td className="px-2 py-3 text-slate-500">{timeAgo(a.createdAt)}</td>
                    <td className="px-2 py-3 text-slate-400">
                      <MoreVertical size={16} />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
