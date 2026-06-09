import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import GlassCard from '../Common/GlassCard'
import RiskBadge from '../Common/RiskBadge'
import { useTopRiskMerchants } from '../../hooks/useDashboard'

export default function TopRiskMerchants() {
  const { data, isLoading, error } = useTopRiskMerchants(5)
  const merchants = data ?? []

  return (
    <GlassCard padding="sm" glowVariant="red" className="flex h-full min-h-0 flex-col !p-3">
      <div className="mb-1.5 flex items-center justify-between">
        <h3 className="text-xs font-semibold text-white">Top High Risk Merchants</h3>
        <Link
          to="/merchants"
          className="flex items-center gap-1 text-[10px] font-medium text-gold hover:underline"
        >
          View all <ArrowRight size={12} />
        </Link>
      </div>

      {isLoading ? (
        <div className="space-y-1.5">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-5 animate-pulse rounded bg-glass-skeleton" />
          ))}
        </div>
      ) : error ? (
        <p className="text-xs text-danger">Could not load</p>
      ) : merchants.length === 0 ? (
        <div className="flex flex-1 items-center justify-center text-[10px] text-glass-muted">
          No merchants available
        </div>
      ) : (
        <ol className="flex min-h-0 flex-1 flex-col justify-between gap-1">
          {merchants.map((m) => (
            <li key={m.merchantId ?? m.rank} className="flex items-center justify-between text-[10px]">
              <div className="flex min-w-0 items-center gap-2">
                <span className="w-4 flex-shrink-0 text-glass-subtle">{m.rank}.</span>
                <span className="truncate text-white/80">
                  {m.name ?? `Merchant #${m.merchantId ?? '—'}`}
                </span>
              </div>
              <div className="flex flex-shrink-0 items-center gap-2">
                {m.riskScore != null && (
                  <span className="font-semibold text-gold">{Math.round(m.riskScore)}</span>
                )}
                <RiskBadge level={m.riskLevel} />
              </div>
            </li>
          ))}
        </ol>
      )}
    </GlassCard>
  )
}
