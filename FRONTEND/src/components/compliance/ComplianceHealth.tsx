import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import GlassCard from '../Common/GlassCard'
import { useComplianceHealth } from '../../hooks/useDashboard'

interface Row {
  label: string
  pct: number
}

function barColor(pct: number) {
  if (pct >= 90) return '#22C55E'
  if (pct >= 75) return '#F59E0B'
  return '#EF4444'
}

export default function ComplianceHealth() {
  const { data, isLoading, error } = useComplianceHealth()

  const rows: Row[] = [
    { label: 'KYC Completion', pct: data?.kycCompletion ?? 0 },
    { label: 'CDD Reviews', pct: data?.cddReviews ?? 0 },
    { label: 'EDD Reviews', pct: data?.eddReviews ?? 0 },
    { label: 'SAR Filing SLA', pct: data?.sarFilingSla ?? 0 },
  ]

  return (
    <GlassCard padding="sm" glowVariant="teal" className="flex h-full min-h-0 flex-col !p-3">
      <div className="mb-1.5 flex items-center justify-between">
        <h3 className="text-xs font-semibold text-white">Compliance Health</h3>
        <Link
          to="/regulatory-reports"
          className="flex items-center gap-1 text-[10px] font-medium text-gold hover:underline"
        >
          View compliance <ArrowRight size={12} />
        </Link>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-5 animate-pulse rounded bg-glass-skeleton" />
          ))}
        </div>
      ) : error ? (
        <p className="text-xs text-danger">Could not load</p>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col justify-between gap-2">
          {rows.map((r) => {
            const color = barColor(r.pct)
            return (
              <div key={r.label}>
                <div className="mb-0.5 flex items-center justify-between text-[10px]">
                  <span className="text-white/70">{r.label}</span>
                  <span className="font-semibold text-white">{r.pct}%</span>
                </div>
                <div className="h-1 w-full overflow-hidden rounded-full bg-burgundy-950/80">
                  <div
                    className="h-full rounded-full transition-all"
                    style={{
                      width: `${r.pct}%`,
                      backgroundColor: color,
                      boxShadow: `0 0 6px ${color}88`,
                    }}
                  />
                </div>
              </div>
            )
          })}
        </div>
      )}
    </GlassCard>
  )
}
