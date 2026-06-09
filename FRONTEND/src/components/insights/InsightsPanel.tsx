import * as Tabs from '@radix-ui/react-tabs'
import { useState } from 'react'
import GlassButton from '../Common/GlassButton'
import GlassCard from '../Common/GlassCard'
import GlassInput from '../Common/GlassInput'
import RiskBadge from '../Common/RiskBadge'
import { useCase, useCases } from '../../features/api/queries'
import { useLiveAlerts } from '../../hooks/useDashboard'
import { cn } from '../../lib/utils'
import type { Alert, Case } from '../../types'

const FILTERS = ['All', 'New', 'Investigating', 'Resolved'] as const
const CASE_STEPS = ['New', 'Under Review', 'Escalated', 'Closed']

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

function alertMatchesFilter(alert: Alert, filter: string) {
  if (filter === 'All') return true
  const status = (alert.status ?? '').toUpperCase()
  if (filter === 'New') return status === 'OPEN'
  if (filter === 'Investigating') return status === 'INVESTIGATING'
  if (filter === 'Resolved') return status === 'RESOLVED'
  return true
}

function stepIndexForStatus(status?: string) {
  switch ((status ?? '').toUpperCase()) {
    case 'NEW':
      return 0
    case 'ASSIGNED':
    case 'IN_PROGRESS':
    case 'PENDING_REVIEW':
    case 'INVESTIGATING':
      return 1
    case 'ESCALATED':
      return 2
    case 'RESOLVED':
    case 'CLOSED_CLEARED':
    case 'CLOSED_SAR_FILED':
    case 'CLOSED_BLOCKED':
    case 'CLOSED_REJECTED':
      return 3
    default:
      return 1
  }
}

function statusLabel(status?: string) {
  switch ((status ?? '').toUpperCase()) {
    case 'NEW':
      return 'New'
    case 'ASSIGNED':
    case 'IN_PROGRESS':
      return 'In Progress'
    case 'PENDING_REVIEW':
      return 'Under Review'
    case 'ESCALATED':
      return 'Escalated'
    case 'RESOLVED':
    case 'CLOSED_CLEARED':
    case 'CLOSED_SAR_FILED':
    case 'CLOSED_BLOCKED':
    case 'CLOSED_REJECTED':
      return 'Closed'
    default:
      return status ?? 'Under Review'
  }
}

function caseDisplayName(c: Case) {
  return c.description?.trim() || c.caseReference
}

function ownerName(c: Case | undefined) {
  if (!c?.assignedTo) return 'Unassigned'
  const { firstName, lastName, username } = c.assignedTo
  const full = `${firstName ?? ''} ${lastName ?? ''}`.trim()
  return full || username || 'Unassigned'
}

const CLOSED_STATUSES = new Set([
  'RESOLVED',
  'CLOSED_CLEARED',
  'CLOSED_SAR_FILED',
  'CLOSED_BLOCKED',
  'CLOSED_REJECTED',
])

export default function InsightsPanel() {
  const { data, isLoading } = useLiveAlerts(12)
  const { data: casesPage, isLoading: caseLoading } = useCases({ page: 0, size: 10 })
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>('All')
  const [search, setSearch] = useState('')

  const featuredCase = casesPage?.content?.find(
    (c) => !CLOSED_STATUSES.has((c.status ?? '').toUpperCase()),
  )
  const { data: caseDetail } = useCase(featuredCase?.id ?? 0)

  const caseRef = featuredCase?.caseReference
  const caseName = featuredCase ? caseDisplayName(featuredCase) : undefined
  const caseRisk = featuredCase?.priority
  const caseStatus = featuredCase?.status
  const caseOwner = ownerName(featuredCase)
  const caseAlertCount = caseDetail?.alerts?.length
  const activeStep = stepIndexForStatus(featuredCase?.status)

  const alerts = (data ?? []).filter((a) => {
    if (!alertMatchesFilter(a, filter)) return false
    if (!search.trim()) return true
    const q = search.toLowerCase()
    return (
      a.alertType?.toLowerCase().includes(q) ||
      a.description?.toLowerCase().includes(q) ||
      String(a.id).includes(q)
    )
  })

  return (
    <aside className="sticky top-0 hidden h-[calc(100vh-7rem)] w-[300px] min-w-[300px] flex-shrink-0 flex-col gap-4 xl:flex">
      <GlassCard className="flex min-h-0 flex-1 flex-col overflow-hidden" padding="md">
        <h3 className="mb-3 text-sm font-semibold text-white">Alerts</h3>

        <div className="mb-3 flex flex-wrap gap-1.5">
          {FILTERS.map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setFilter(f)}
              className={cn(
                'rounded-lg px-2.5 py-1 text-[11px] font-medium transition-colors',
                filter === f
                  ? 'hokeka-nav-active text-white'
                  : 'border border-glass-border bg-burgundy-950/60 text-white/55 hover:border-glass-border-hover hover:bg-burgundy-850/60 hover:text-white/90',
              )}
            >
              {f}
            </button>
          ))}
        </div>

        <GlassInput
          placeholder="Search alerts..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          showSearchIcon
          className="mb-3"
        />

        <div className="flex-1 space-y-2 overflow-y-auto pr-1">
          {isLoading ? (
            Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-14 animate-pulse rounded-xl bg-glass-skeleton" />
            ))
          ) : alerts.length === 0 ? (
            <p className="py-8 text-center text-xs text-glass-muted">No alerts</p>
          ) : (
            alerts.map((a) => (
              <div
                key={a.id}
                className="rounded-xl border border-glass-border bg-glass-surface p-3 transition-colors hover:border-glass-border-hover hover:bg-burgundy-850/55"
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="truncate text-xs font-medium text-white">{a.alertType}</p>
                  <RiskBadge level={a.priority} />
                </div>
                <p className="mt-1 truncate text-[11px] text-glass-muted">
                  {a.description || `#${a.id}`}
                </p>
                <p className="mt-1.5 text-[10px] text-glass-subtle">{timeAgo(a.createdAt)}</p>
              </div>
            ))
          )}
        </div>
      </GlassCard>

      <GlassCard className="flex-shrink-0" padding="md" static>
        <h3 className="mb-3 text-sm font-semibold text-white">Case Details</h3>
        {caseLoading ? (
          <div className="h-40 animate-pulse rounded-xl bg-glass-skeleton" />
        ) : !featuredCase ? (
          <p className="py-8 text-center text-xs text-glass-muted">No active cases</p>
        ) : (
          <>
            <p className="text-xs font-medium text-gold">#{caseRef}</p>
            <div className="mt-1 flex items-center gap-2">
              <p className="text-sm font-semibold text-white">{caseName}</p>
              <span className="rounded-md bg-warning/20 px-2 py-0.5 text-[10px] font-semibold text-warning">
                {statusLabel(caseStatus)}
              </span>
            </div>

            <div className="mt-3 grid grid-cols-2 gap-2 text-[11px]">
              <div>
                <p className="text-glass-subtle">Risk</p>
                {caseRisk ? <RiskBadge level={caseRisk} className="mt-1" /> : <p className="mt-1 text-white/50">—</p>}
              </div>
              <div>
                <p className="text-glass-subtle">Alerts</p>
                <p className="mt-1 font-semibold text-white">
                  {caseAlertCount !== undefined ? caseAlertCount : '—'}
                </p>
              </div>
              <div className="col-span-2">
                <p className="text-glass-subtle">Owner</p>
                <p className="mt-0.5 font-medium text-white">{caseOwner}</p>
              </div>
            </div>

            <Tabs.Root defaultValue="summary" className="mt-4">
              <Tabs.List className="mb-3 flex gap-3 overflow-x-auto border-b border-glass-border pb-2">
                {['Summary', 'Alerts', 'Timeline', 'Notes'].map((tab) => (
                  <Tabs.Trigger
                    key={tab}
                    value={tab.toLowerCase()}
                    className="whitespace-nowrap text-[10px] font-medium text-glass-muted outline-none transition-colors data-[state=active]:text-gold"
                  >
                    {tab}
                  </Tabs.Trigger>
                ))}
              </Tabs.List>
              <Tabs.Content value="summary" className="outline-none">
                <div className="flex items-center justify-between gap-1">
                  {CASE_STEPS.map((step, i) => (
                    <div key={step} className="flex flex-1 flex-col items-center">
                      <div
                        className={cn(
                          'flex h-6 w-6 items-center justify-center rounded-full text-[9px] font-bold',
                          i <= activeStep
                            ? 'hokeka-nav-active text-white'
                            : 'bg-burgundy-950/80 text-white/35',
                        )}
                      >
                        {i + 1}
                      </div>
                      <span className="mt-1 text-center text-[8px] leading-tight text-glass-muted">
                        {step}
                      </span>
                    </div>
                  ))}
                </div>
              </Tabs.Content>
            </Tabs.Root>

            <div className="mt-4 flex flex-wrap gap-2">
              <GlassButton size="sm" variant="default">
                Add Note
              </GlassButton>
              <GlassButton size="sm" variant="default">
                Request Info
              </GlassButton>
              <GlassButton size="sm" variant="default">
                Escalate Case
              </GlassButton>
              <GlassButton size="sm" variant="primary">
                Close Case
              </GlassButton>
            </div>
          </>
        )}
      </GlassCard>
    </aside>
  )
}
