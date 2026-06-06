import {
  ArrowRight,
  Eye,
  Newspaper,
  ShieldAlert,
  User,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { useScreeningResultsToday } from '../../hooks/useDashboard'

interface Row {
  label: string
  icon: LucideIcon
  iconColor: string
  value: number
}

export default function ScreeningResults() {
  const { data, isLoading, error } = useScreeningResultsToday()

  const rows: Row[] = [
    { label: 'PEP Matches', icon: User, iconColor: '#F59E0B', value: data?.pepMatches ?? 0 },
    { label: 'Sanctions Matches', icon: ShieldAlert, iconColor: '#DC2626', value: data?.sanctionsMatches ?? 0 },
    { label: 'Adverse Media Hits', icon: Newspaper, iconColor: '#F97316', value: data?.adverseMediaHits ?? 0 },
    { label: 'Watchlist Matches', icon: Eye, iconColor: '#1F6FEB', value: data?.watchlistMatches ?? 0 },
  ]

  return (
    <div className="flex h-full flex-col rounded-2xl border border-hokeka-border bg-hokeka-card p-6">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-base font-semibold text-slate-900">Screening Results (Today)</h3>
        <Link
          to="/screening"
          className="flex items-center gap-1 text-sm font-medium text-hokeka-secondary hover:underline"
        >
          View screening <ArrowRight size={14} />
        </Link>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-10 animate-pulse rounded bg-slate-100" />
          ))}
        </div>
      ) : error ? (
        <p className="text-sm text-hokeka-critical">Could not load</p>
      ) : (
        <div className="flex flex-1 flex-col justify-between gap-3">
          {rows.map((r) => {
            const Icon = r.icon
            return (
              <div
                key={r.label}
                className="flex items-center justify-between rounded-lg px-2 py-1.5"
              >
                <div className="flex items-center gap-3">
                  <span
                    className="flex h-8 w-8 items-center justify-center rounded-lg"
                    style={{ backgroundColor: `${r.iconColor}1A` }}
                  >
                    <Icon size={16} color={r.iconColor} />
                  </span>
                  <span className="text-sm text-slate-700">{r.label}</span>
                </div>
                <span className="text-lg font-semibold text-slate-900">{r.value}</span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
