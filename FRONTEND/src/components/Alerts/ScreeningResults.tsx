import {
  Eye,
  Newspaper,
  ShieldAlert,
  User,
  type LucideIcon,
} from 'lucide-react'
import DashboardPanel from '../dashboard/DashboardPanel'
import DashboardWidgetHeader from '../dashboard/DashboardWidgetHeader'
import { DashboardEmpty, DashboardError, DashboardLoading } from '../dashboard/DashboardState'
import { useScreeningResultsToday } from '../../hooks/useDashboard'

interface Row {
  label: string
  icon: LucideIcon
  tone: string
  value: number | undefined
}

/** Answers: What watchlist hit types fired today? */
export default function ScreeningResults() {
  const { data, isLoading, error } = useScreeningResultsToday()

  const rows: Row[] = [
    {
      label: 'PEP matches',
      icon: User,
      tone: 'bg-[var(--db-warning-soft)] text-[var(--db-warning)]',
      value: data?.pepMatches,
    },
    {
      label: 'Sanctions matches',
      icon: ShieldAlert,
      tone: 'bg-[var(--db-danger-soft)] text-[var(--db-danger)]',
      value: data?.sanctionsMatches,
    },
    {
      label: 'Adverse media',
      icon: Newspaper,
      tone: 'bg-[var(--db-info-soft)] text-[var(--db-info)]',
      value: data?.adverseMediaHits,
    },
    {
      label: 'Watchlist matches',
      icon: Eye,
      tone: 'bg-[var(--db-accent-soft)] text-[var(--db-accent)]',
      value: data?.watchlistMatches,
    },
  ]

  const total = rows.reduce((s, r) => s + (r.value ?? 0), 0)

  return (
    <DashboardPanel aria-labelledby="db-screening-results" className="min-h-[200px]">
      <DashboardWidgetHeader
        id="db-screening-results"
        title="Screening hits today"
        description="Match types from today’s screening runs"
        actionLabel="Screening"
        actionTo="/screening"
      />

      {isLoading && !data ? (
        <DashboardLoading rows={4} />
      ) : error ? (
        <DashboardError message="Screening results unavailable." />
      ) : total === 0 ? (
        <DashboardEmpty message="No screening hits recorded today." />
      ) : (
        <ul className="flex min-h-0 flex-1 flex-col justify-between gap-1">
          {rows.map((r) => {
            const Icon = r.icon
            return (
              <li
                key={r.label}
                className="flex items-center justify-between rounded-[var(--db-radius-sm)] px-0.5 py-1.5"
              >
                <div className="flex items-center gap-2">
                  <span
                    className={`flex h-7 w-7 items-center justify-center rounded-md ${r.tone}`}
                    aria-hidden
                  >
                    <Icon size={13} />
                  </span>
                  <span className="text-xs text-[var(--db-text-secondary)]">{r.label}</span>
                </div>
                <span className="text-sm font-semibold tabular-nums text-[var(--db-text)]">
                  {r.value === undefined ? '—' : r.value}
                </span>
              </li>
            )
          })}
        </ul>
      )}
    </DashboardPanel>
  )
}
