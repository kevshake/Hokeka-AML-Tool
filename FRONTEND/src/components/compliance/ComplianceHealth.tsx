import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useComplianceHealth } from '../../hooks/useDashboard'

interface Row {
  label: string
  pct: number | null
}

function barColor(pct: number) {
  if (pct >= 90) return 'bg-hokeka-success'
  if (pct >= 70) return 'bg-hokeka-warning'
  return 'bg-hokeka-critical'
}

export default function ComplianceHealth() {
  const { data, isLoading, error } = useComplianceHealth()

  const rows: Row[] = [
    { label: 'KYC Completion', pct: data?.kycCompletion ?? null },
    { label: 'CDD Reviews', pct: data?.cddReviews ?? null },
    { label: 'EDD Reviews', pct: data?.eddReviews ?? null },
    { label: 'SAR Filing SLA', pct: data?.sarFilingSla ?? null },
  ]

  return (
    <div className="flex h-full flex-col rounded-2xl border border-hokeka-border bg-hokeka-card p-6">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-base font-semibold text-slate-900">Compliance Health</h3>
        <Link
          to="/regulatory-reports"
          className="flex items-center gap-1 text-sm font-medium text-hokeka-secondary hover:underline"
        >
          View compliance <ArrowRight size={14} />
        </Link>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-8 animate-pulse rounded bg-slate-100" />
          ))}
        </div>
      ) : error ? (
        <p className="text-sm text-hokeka-critical">Could not load</p>
      ) : (
        <div className="flex flex-1 flex-col justify-between gap-4">
          {rows.map((r) => {
            const pct = r.pct ?? 0
            return (
              <div key={r.label}>
                <div className="mb-1.5 flex items-center justify-between text-sm">
                  <span className="text-slate-600">{r.label}</span>
                  <span className="font-semibold text-slate-900">
                    {r.pct === null ? '—' : `${r.pct}%`}
                  </span>
                </div>
                <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100">
                  <div
                    style={{ width: `${pct}%` }}
                    className={`h-full ${barColor(pct)}`}
                  />
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
