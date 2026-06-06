import { ArrowDown, ArrowRight, ArrowUp } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer } from 'recharts'
import { useCasesClosedRecent, useCasesPriority } from '../../hooks/useDashboard'

const PRIORITY_COLORS: Record<string, string> = {
  CRITICAL: '#DC2626',
  HIGH: '#F59E0B',
  MEDIUM: '#1F6FEB',
  LOW: '#00A86B',
}

export default function InvestigationCases() {
  const { data, isLoading, error } = useCasesPriority()
  const closed = useCasesClosedRecent()

  const counts = data ?? {}
  const entries = Object.entries(counts)
    .filter(([k]) => k !== 'null')
    .map(([k, v]) => ({ name: k, value: Number(v) || 0 }))
  const total = entries.reduce((sum, e) => sum + e.value, 0)
  const pct = (n: number) => (total > 0 ? Math.round((n / total) * 100) : 0)

  return (
    <div className="flex h-full flex-col rounded-2xl border border-hokeka-border bg-hokeka-card p-6">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-base font-semibold text-slate-900">Investigation Cases</h3>
        <Link
          to="/cases"
          className="flex items-center gap-1 text-sm font-medium text-hokeka-secondary hover:underline"
        >
          View all cases <ArrowRight size={14} />
        </Link>
      </div>

      {isLoading ? (
        <div className="h-48 animate-pulse rounded bg-slate-100" />
      ) : error ? (
        <p className="text-sm text-hokeka-critical">Could not load</p>
      ) : (
        <div className="grid flex-1 grid-cols-2 gap-6 divide-x divide-hokeka-border">
          {/* By Status (priority groups) */}
          <div>
            <p className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-400">
              By Priority
            </p>
            {total === 0 ? (
              <div className="flex h-40 items-center justify-center text-sm text-slate-400">
                No open cases
              </div>
            ) : (
              <div className="flex items-center gap-4">
                <div className="relative h-32 w-32">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={entries}
                        innerRadius={42}
                        outerRadius={60}
                        dataKey="value"
                        stroke="none"
                        isAnimationActive={false}
                      >
                        {entries.map((e) => (
                          <Cell
                            key={e.name}
                            fill={PRIORITY_COLORS[e.name] ?? '#94A3B8'}
                          />
                        ))}
                      </Pie>
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="absolute inset-0 flex flex-col items-center justify-center">
                    <p className="text-xl font-bold text-slate-900">{total}</p>
                    <p className="text-xs text-slate-400">Total</p>
                  </div>
                </div>

                <div className="flex flex-1 flex-col gap-2">
                  {entries.map((e) => (
                    <div
                      key={e.name}
                      className="flex items-center justify-between text-sm"
                    >
                      <div className="flex items-center gap-2">
                        <span
                          className="inline-block h-2.5 w-2.5 rounded-full"
                          style={{
                            backgroundColor: PRIORITY_COLORS[e.name] ?? '#94A3B8',
                          }}
                        />
                        <span className="text-slate-600">{e.name}</span>
                      </div>
                      <span className="font-medium text-slate-900">
                        {e.value}{' '}
                        <span className="text-xs text-slate-400">({pct(e.value)}%)</span>
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Recently Closed — fed by /dashboard/cases/closed-recent */}
          <div className="pl-6">
            <p className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-400">
              Recently Closed (30d)
            </p>
            {closed.isLoading ? (
              <div className="h-40 animate-pulse rounded bg-slate-100" />
            ) : closed.error ? (
              <p className="text-sm text-hokeka-critical">Could not load</p>
            ) : (
              <div className="flex h-40 flex-col justify-between">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-2xl font-bold text-slate-900">
                      {closed.data?.closureRate ?? 0}%
                    </p>
                    <p className="text-xs text-slate-400">Closure rate</p>
                  </div>
                  <ClosureSpark sparkline={closed.data?.sparkline ?? []} />
                </div>
                <div className="flex items-center gap-1 text-xs font-medium">
                  {(closed.data?.closureRateTrend ?? 0) >= 0 ? (
                    <span className="flex items-center gap-1 text-hokeka-success">
                      <ArrowUp size={12} />
                      {Math.abs(closed.data?.closureRateTrend ?? 0)} pts vs prev
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-hokeka-critical">
                      <ArrowDown size={12} />
                      {Math.abs(closed.data?.closureRateTrend ?? 0)} pts vs prev
                    </span>
                  )}
                </div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <p className="text-slate-400">Resolved</p>
                    <p className="font-semibold text-slate-900">
                      {closed.data?.resolved ?? 0}
                    </p>
                  </div>
                  <div>
                    <p className="text-slate-400">False positive</p>
                    <p className="font-semibold text-slate-900">
                      {closed.data?.falsePositive ?? 0}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function ClosureSpark({ sparkline }: { sparkline: number[] }) {
  if (!sparkline.length) return <div className="h-8 w-24" />
  const data = sparkline.map((v, i) => ({ i, v }))
  return (
    <div className="h-8 w-24">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data}>
          <Line
            type="monotone"
            dataKey="v"
            stroke="#00A86B"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

// Re-export visual primitives for potential reuse
export function ClosureRateMini({ pct, trend }: { pct: number; trend: number }) {
  const data = [10, 18, 14, 22, 26, 31, 28].map((v, i) => ({ i, v }))
  return (
    <div className="flex items-center justify-between">
      <div>
        <p className="text-2xl font-bold text-slate-900">{pct}%</p>
        <div className="flex items-center gap-1 text-xs font-medium text-hokeka-success">
          <ArrowUp size={12} />+{trend}%
        </div>
      </div>
      <div className="h-8 w-24">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data}>
            <Line
              type="monotone"
              dataKey="v"
              stroke="#00A86B"
              strokeWidth={2}
              dot={false}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
