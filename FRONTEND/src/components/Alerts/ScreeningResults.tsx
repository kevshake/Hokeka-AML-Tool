import {
  ArrowRight,
  Eye,
  Newspaper,
  ShieldAlert,
  User,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import GlassCard from '../Common/GlassCard'
import { useScreeningResultsToday } from '../../hooks/useDashboard'

interface Row {
  label: string
  icon: LucideIcon
  iconColor: string
  value: number | undefined
}

export default function ScreeningResults() {
  const { data, isLoading, error } = useScreeningResultsToday()

  const rows: Row[] = [
    { label: 'PEP Matches', icon: User, iconColor: '#F59E0B', value: data?.pepMatches },
    { label: 'Sanctions Matches', icon: ShieldAlert, iconColor: '#DC2626', value: data?.sanctionsMatches },
    { label: 'Adverse Media Hits', icon: Newspaper, iconColor: '#F97316', value: data?.adverseMediaHits },
    { label: 'Watchlist Matches', icon: Eye, iconColor: '#C9A96E', value: data?.watchlistMatches },
  ]

  return (
    <GlassCard padding="sm" glowVariant="purple" className="flex h-full min-h-0 flex-col !p-3">
      <div className="mb-1.5 flex items-center justify-between">
        <h3 className="text-xs font-semibold text-white">Watchlist Matches</h3>
        <Link
          to="/screening"
          className="flex items-center gap-1 text-[10px] font-medium text-gold hover:underline"
        >
          View screening <ArrowRight size={12} />
        </Link>
      </div>

      {isLoading ? (
        <div className="space-y-1.5">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-7 animate-pulse rounded bg-glass-skeleton" />
          ))}
        </div>
      ) : error ? (
        <p className="text-xs text-danger">Could not load</p>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col justify-between gap-1">
          {rows.map((r) => {
            const Icon = r.icon
            return (
              <div
                key={r.label}
                className="flex items-center justify-between rounded-lg px-0.5 py-0.5 transition-colors hover:bg-burgundy-850/40"
              >
                <div className="flex items-center gap-2">
                  <span
                    className="flex h-6 w-6 items-center justify-center rounded-md"
                    style={{ backgroundColor: `${r.iconColor}22` }}
                  >
                    <Icon size={12} color={r.iconColor} />
                  </span>
                  <span className="text-[10px] text-white/75">{r.label}</span>
                </div>
                <span className="text-sm font-semibold text-white">
                  {r.value === undefined ? '—' : r.value}
                </span>
              </div>
            )
          })}
        </div>
      )}
    </GlassCard>
  )
}
