import { ArrowDown, ArrowRight, ArrowUp } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import GlassCard from '../Common/GlassCard'
import Sparkline from '../kpi/Sparkline'
import { useCasesClosedRecent, useCasesPriority } from '../../hooks/useDashboard'

const PRIORITY_COLORS: Record<string, string> = {
  CRITICAL: '#DC2626',
  HIGH: '#F59E0B',
  MEDIUM: '#C9A96E',
  LOW: '#22C55E',
}

export default function InvestigationCases() {
  const { data, isLoading, error } = useCasesPriority()
  const closed = useCasesClosedRecent()

  const counts = data ?? {}
  const entries = Object.entries(counts)
    .filter(([k]) => k !== 'null')
    .map(([k, v]) => ({ name: k, value: Number(v) || 0 }))
    .filter((e) => e.value > 0)

  const total = entries.reduce((sum, e) => sum + e.value, 0)
  const pct = (n: number) => (total > 0 ? Math.round((n / total) * 100) : 0)

  return (
    <GlassCard padding="sm" glowVariant="orange" className="flex h-full min-h-0 flex-col !p-3">
      <div className="mb-1.5 flex items-center justify-between">
        <h3 className="text-xs font-semibold text-white">Investigation Cases</h3>
        <Link
          to="/cases"
          className="flex items-center gap-1 text-[10px] font-medium text-gold hover:underline"
        >
          View all cases <ArrowRight size={12} />
        </Link>
      </div>

      {isLoading ? (
        <div className="flex-1 animate-pulse rounded bg-glass-skeleton" />
      ) : error ? (
        <p className="text-xs text-danger">Could not load</p>
      ) : (
        <div className="grid min-h-0 flex-1 grid-cols-1 gap-2 divide-y divide-glass-border lg:grid-cols-2 lg:divide-x lg:divide-y-0">
          <div className="pb-2 lg:pb-0 lg:pr-2">
            <p className="mb-1 text-[9px] font-semibold uppercase tracking-widest text-glass-muted">
              By Priority
            </p>
            {entries.length === 0 ? (
              <div className="flex h-20 items-center justify-center text-[10px] text-glass-muted">
                No open cases
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <div className="relative h-20 w-20 flex-shrink-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={entries}
                        innerRadius={26}
                        outerRadius={38}
                        dataKey="value"
                        stroke="none"
                        isAnimationActive={false}
                      >
                        {entries.map((e, i) => (
                          <Cell
                            key={e.name}
                            fill={
                              PRIORITY_COLORS[e.name] ??
                              ['#DC2626', '#F59E0B', '#C9A96E', '#22C55E'][i % 4]
                            }
                          />
                        ))}
                      </Pie>
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="absolute inset-0 flex flex-col items-center justify-center">
                    <p className="text-lg font-bold leading-none text-white">{total}</p>
                    <p className="text-[9px] text-glass-muted">Total</p>
                  </div>
                </div>

                <div className="flex flex-1 flex-col gap-1">
                  {entries.map((e, i) => (
                    <div key={e.name} className="flex items-center justify-between text-[10px]">
                      <div className="flex items-center gap-1.5">
                        <span
                          className="inline-block h-2 w-2 rounded-full"
                          style={{
                            backgroundColor:
                              PRIORITY_COLORS[e.name] ??
                              ['#DC2626', '#F59E0B', '#C9A96E', '#22C55E'][i % 4],
                          }}
                        />
                        <span className="text-white/70">{e.name}</span>
                      </div>
                      <span className="font-medium text-white">
                        {e.value}{' '}
                        <span className="text-glass-subtle">({pct(e.value)}%)</span>
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div className="pt-2 lg:pl-2 lg:pt-0">
            <p className="mb-1 text-[9px] font-semibold uppercase tracking-widest text-glass-muted">
              Recently Closed (30d)
            </p>
            {closed.isLoading ? (
              <div className="h-24 animate-pulse rounded bg-glass-skeleton" />
            ) : closed.error ? (
              <p className="text-xs text-danger">Could not load</p>
            ) : (
              <div className="flex h-24 flex-col justify-between">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-xl font-bold leading-none text-white">
                      {closed.data?.closureRate ?? 0}%
                    </p>
                    <p className="text-[9px] text-glass-muted">Closure rate</p>
                  </div>
                  <Sparkline
                    data={closed.data?.sparkline}
                    color="#22C55E"
                    glow="green"
                    height={32}
                    width={80}
                  />
                </div>
                <div className="flex items-center gap-1 text-[9px] font-medium">
                  {(closed.data?.closureRateTrend ?? 0) >= 0 ? (
                    <span className="flex items-center gap-1 text-success">
                      <ArrowUp size={11} />
                      {Math.abs(closed.data?.closureRateTrend ?? 0)} pts vs prev
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-danger">
                      <ArrowDown size={11} />
                      {Math.abs(closed.data?.closureRateTrend ?? 0)} pts vs prev
                    </span>
                  )}
                </div>
                <div className="grid grid-cols-2 gap-1.5 text-[9px]">
                  <div>
                    <p className="text-glass-muted">Resolved</p>
                    <p className="font-semibold text-white">{closed.data?.resolved ?? 0}</p>
                  </div>
                  <div>
                    <p className="text-glass-muted">False positive</p>
                    <p className="font-semibold text-white">{closed.data?.falsePositive ?? 0}</p>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </GlassCard>
  )
}
