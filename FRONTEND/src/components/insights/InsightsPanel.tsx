import * as Tabs from '@radix-ui/react-tabs'
import { Search } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardPanel from '../dashboard/DashboardPanel'
import DashboardRiskBadge from '../dashboard/DashboardRiskBadge'
import { DashboardEmpty, DashboardLoading } from '../dashboard/DashboardState'
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

/** Right rail: filterable alert stream + featured open case. */
export default function InsightsPanel() {
  const { data, isLoading } = useLiveAlerts(12)
  const { data: casesPage, isLoading: caseLoading, error: caseError } = useCases({ page: 0, size: 10 })
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
    <aside className="flex w-full flex-shrink-0 flex-col gap-4 xl:sticky xl:top-0 xl:w-[300px] xl:min-w-[300px]">
      <DashboardPanel aria-labelledby="db-insights-alerts" padding="md" className="min-h-0 flex-1">
        <div className="mb-3 flex items-center justify-between gap-2">
          <h3 id="db-insights-alerts" className="db-title">
            Alert inbox
          </h3>
          <Link to="/alerts" className="db-link text-[11px]">
            Open alerts
          </Link>
        </div>

        <div className="mb-3 flex flex-wrap gap-1.5" role="tablist" aria-label="Alert status filter">
          {FILTERS.map((f) => (
            <button
              key={f}
              type="button"
              role="tab"
              aria-selected={filter === f}
              onClick={() => setFilter(f)}
              className={cn('db-chip', filter === f && 'db-chip--active')}
            >
              {f}
            </button>
          ))}
        </div>

        <div className="relative mb-3">
          <Search
            size={14}
            className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--db-text-muted)]"
            aria-hidden
          />
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search alerts…"
            className="db-input"
            aria-label="Search alerts"
          />
        </div>

        <div className="max-h-[320px] flex-1 space-y-2 overflow-y-auto pr-0.5 xl:max-h-[calc(100vh-28rem)]">
          {isLoading && !data ? (
            <DashboardLoading rows={4} />
          ) : alerts.length === 0 ? (
            <DashboardEmpty message="No alerts match this filter." />
          ) : (
            alerts.map((a) => (
              <article
                key={a.id}
                className="rounded-[var(--db-radius-sm)] border border-[var(--db-border)] bg-[var(--db-surface)] p-3 transition-colors hover:bg-[var(--db-surface-muted)]"
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="truncate text-xs font-medium text-[var(--db-text)]">{a.alertType}</p>
                  <DashboardRiskBadge level={a.priority} />
                </div>
                <p className="mt-1 truncate text-[11px] text-[var(--db-text-muted)]">
                  {a.description || `#${a.id}`}
                </p>
                <p className="mt-1.5 text-[10px] text-[var(--db-text-muted)]">{timeAgo(a.createdAt)}</p>
              </article>
            ))
          )}
        </div>
      </DashboardPanel>

      <DashboardPanel aria-labelledby="db-insights-case" padding="md" flat>
        <h3 id="db-insights-case" className="db-title mb-3">
          Featured case
        </h3>
        {caseLoading && !casesPage ? (
          <DashboardLoading rows={5} />
        ) : caseError ? (
          <p className="db-error text-left" role="alert">
            Cases could not be loaded.
          </p>
        ) : !featuredCase ? (
          <DashboardEmpty message="No active cases to feature." />
        ) : (
          <>
            <p className="text-xs font-medium text-[var(--db-accent)]">#{caseRef}</p>
            <div className="mt-1 flex flex-wrap items-center gap-2">
              <p className="text-sm font-semibold text-[var(--db-text)]">{caseName}</p>
              <span className="db-chip db-chip--warning">{statusLabel(caseStatus)}</span>
            </div>

            <div className="mt-3 grid grid-cols-2 gap-2 text-[11px]">
              <div>
                <p className="text-[var(--db-text-muted)]">Risk</p>
                {caseRisk ? (
                  <div className="mt-1">
                    <DashboardRiskBadge level={caseRisk} />
                  </div>
                ) : (
                  <p className="mt-1 text-[var(--db-text-muted)]">—</p>
                )}
              </div>
              <div>
                <p className="text-[var(--db-text-muted)]">Linked alerts</p>
                <p className="mt-1 font-semibold tabular-nums text-[var(--db-text)]">
                  {caseAlertCount !== undefined ? caseAlertCount : '—'}
                </p>
              </div>
              <div className="col-span-2">
                <p className="text-[var(--db-text-muted)]">Owner</p>
                <p className="mt-0.5 font-medium text-[var(--db-text)]">{caseOwner}</p>
              </div>
            </div>

            <Tabs.Root defaultValue="summary" className="mt-4">
              <Tabs.List className="db-tabs mb-3" aria-label="Case detail tabs">
                {['Summary', 'Alerts', 'Timeline', 'Notes'].map((tab) => (
                  <Tabs.Trigger key={tab} value={tab.toLowerCase()} className="db-tab">
                    {tab}
                  </Tabs.Trigger>
                ))}
              </Tabs.List>
              <Tabs.Content value="summary" className="outline-none">
                <ol className="flex items-center justify-between gap-1">
                  {CASE_STEPS.map((step, i) => (
                    <li key={step} className="flex flex-1 flex-col items-center">
                      <span
                        className={cn(
                          'flex h-6 w-6 items-center justify-center rounded-full text-[9px] font-bold',
                          i <= activeStep
                            ? 'bg-[var(--db-accent)] text-white'
                            : 'bg-[#eceef2] text-[var(--db-text-muted)]',
                        )}
                        aria-current={i === activeStep ? 'step' : undefined}
                      >
                        {i + 1}
                      </span>
                      <span className="mt-1 text-center text-[8px] leading-tight text-[var(--db-text-muted)]">
                        {step}
                      </span>
                    </li>
                  ))}
                </ol>
              </Tabs.Content>
              <Tabs.Content value="alerts" className="outline-none">
                <p className="text-xs text-[var(--db-text-muted)]">
                  {caseAlertCount != null
                    ? `${caseAlertCount} alert(s) linked to this case.`
                    : 'Open the case to review linked alerts.'}
                </p>
              </Tabs.Content>
              <Tabs.Content value="timeline" className="outline-none">
                <p className="text-xs text-[var(--db-text-muted)]">
                  Full timeline is available on the case record.
                </p>
              </Tabs.Content>
              <Tabs.Content value="notes" className="outline-none">
                <p className="text-xs text-[var(--db-text-muted)]">
                  Add investigator notes from the case workspace.
                </p>
              </Tabs.Content>
            </Tabs.Root>

            <div className="mt-4 flex flex-wrap gap-2">
              <Link to={`/cases/all?caseId=${featuredCase.id}&action=note`} className="db-btn">
                Add note
              </Link>
              <Link to={`/cases/all?caseId=${featuredCase.id}&action=request-info`} className="db-btn">
                Request info
              </Link>
              <Link to={`/cases/all?caseId=${featuredCase.id}&action=escalate`} className="db-btn">
                Escalate
              </Link>
              <Link
                to={`/cases/all?caseId=${featuredCase.id}&action=close`}
                className="db-btn db-btn--primary"
              >
                Close case
              </Link>
            </div>
          </>
        )}
      </DashboardPanel>
    </aside>
  )
}
