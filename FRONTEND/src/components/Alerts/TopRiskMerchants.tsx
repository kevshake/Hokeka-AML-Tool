import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useTopRiskMerchants } from '../../hooks/useDashboard'

function bandColor(level: string | undefined) {
  switch ((level ?? '').toUpperCase()) {
    case 'CRITICAL':
    case 'HIGH':
      return 'text-hokeka-critical'
    case 'MEDIUM':
      return 'text-hokeka-warning'
    case 'LOW':
      return 'text-hokeka-success'
    default:
      return 'text-slate-500'
  }
}

export default function TopRiskMerchants() {
  const { data, isLoading, error } = useTopRiskMerchants(5)
  const merchants = data ?? []

  return (
    <div className="flex h-full flex-col rounded-2xl border border-hokeka-border bg-hokeka-card p-6">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-base font-semibold text-slate-900">Top High Risk Merchants</h3>
        <Link
          to="/merchants"
          className="flex items-center gap-1 text-sm font-medium text-hokeka-secondary hover:underline"
        >
          View all <ArrowRight size={14} />
        </Link>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-8 animate-pulse rounded bg-slate-100" />
          ))}
        </div>
      ) : error ? (
        <p className="text-sm text-hokeka-critical">Could not load</p>
      ) : merchants.length === 0 ? (
        <div className="flex flex-1 items-center justify-center text-sm text-slate-400">
          No merchants available
        </div>
      ) : (
        <ol className="flex flex-1 flex-col justify-between gap-2">
          {merchants.map((m) => (
            <li
              key={m.merchantId ?? m.rank}
              className="flex items-center justify-between text-sm"
            >
              <div className="flex items-center gap-3">
                <span className="w-4 text-xs font-medium text-slate-400">{m.rank}.</span>
                <span className="truncate text-slate-700">
                  {m.name ?? `Merchant #${m.merchantId ?? '—'}`}
                </span>
              </div>
              <span className={`font-semibold ${bandColor(m.riskLevel)}`}>
                {m.riskLevel ?? '—'}
              </span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
