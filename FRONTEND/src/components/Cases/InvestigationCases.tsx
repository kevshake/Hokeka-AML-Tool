import { ArrowDown, ArrowUp } from 'lucide-react'
import { Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer } from 'recharts'
import DashboardPanel from '../dashboard/DashboardPanel'
import DashboardWidgetHeader from '../dashboard/DashboardWidgetHeader'
import { DashboardEmpty, DashboardError, DashboardLoading } from '../dashboard/DashboardState'
import { normalizeSparklineData } from '../kpi/Sparkline'
import { useCasesClosedRecent, useCasesPriority } from '../../hooks/useDashboard'

const PRIORITY_COLORS: Record<string, string> = {
  CRITICAL: '#d92d20',
  HIGH: '#b54708',
  MEDIUM: '#175cd3',
  LOW: '#079455',
}

/** Answers: How is open case workload split by priority, and are we closing cases? */
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
  const spark = normalizeSparklineData(closed.data?.sparkline)?.map((v, i) => ({ i, v }))

  return (
    <DashboardPanel aria-labelledby="db-investigation-cases" className="min-h-0 flex-1">
      <DashboardWidgetHeader
        id="db-investigation-cases"
        title="Investigation workload"
        description="Open cases by priority and recent closure rate"
        actionLabel="All cases"
        actionTo="/cases"
      />

      {isLoading && !data ? (
        <DashboardLoading rows={4} />
      ) : error ? (
        <DashboardError message="Case priority breakdown unavailable." />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 sm:divide-x sm:divide-[var(--db-border)]">
          <div className="sm:pr-4">
            <p className="db-section-label mb-2">By priority</p>
            {entries.length === 0 ? (
              <DashboardEmpty message="No open cases." />
            ) : (
              <div className="flex items-center gap-3">
                <div className="relative h-[88px] w-[88px] flex-shrink-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={entries}
                        innerRadius={28}
                        outerRadius={40}
                        dataKey="value"
                        stroke="none"
                        isAnimationActive={false}
                      >
                        {entries.map((e, i) => (
                          <Cell
                            key={e.name}
                            fill={
                              PRIORITY_COLORS[e.name] ??
                              ['#d92d20', '#b54708', '#175cd3', '#079455'][i % 4]
                            }
                          />
                        ))}
                      </Pie>
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="absolute inset-0 flex flex-col items-center justify-center">
                    <p className="text-lg font-semibold tabular-nums leading-none text-[var(--db-text)]">
                      {total}
                    </p>
                    <p className="text-[9px] text-[var(--db-text-muted)]">Open</p>
                  </div>
                </div>
                <ul className="flex flex-1 flex-col gap-1">
                  {entries.map((e, i) => (
                    <li key={e.name} className="flex items-center justify-between text-xs">
                      <div className="flex items-center gap-1.5">
                        <span
                          className="inline-block h-2 w-2 rounded-full"
                          style={{
                            backgroundColor:
                              PRIORITY_COLORS[e.name] ??
                              ['#d92d20', '#b54708', '#175cd3', '#079455'][i % 4],
                          }}
                          aria-hidden
                        />
                        <span className="text-[var(--db-text-secondary)]">{e.name}</span>
                      </div>
                      <span className="tabular-nums font-medium text-[var(--db-text)]">
                        {e.value}{' '}
                        <span className="font-normal text-[var(--db-text-muted)]">({pct(e.value)}%)</span>
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          <div className="sm:pl-4">
            <p className="db-section-label mb-2">Closed (30d)</p>
            {closed.isLoading && !closed.data ? (
              <DashboardLoading rows={3} />
            ) : closed.error ? (
              <DashboardError message="Closure metrics unavailable." />
            ) : (
              <div className="flex h-full min-h-[88px] flex-col justify-between gap-2">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-2xl font-semibold tabular-nums leading-none text-[var(--db-text)]">
                      {closed.data?.closureRate ?? 0}%
                    </p>
                    <p className="mt-1 text-[10px] text-[var(--db-text-muted)]">Closure rate</p>
                  </div>
                  {spark ? (
                    <div className="h-8 w-20" aria-hidden>
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={spark} margin={{ top: 2, right: 0, bottom: 2, left: 0 }}>
                          <Line
                            type="monotone"
                            dataKey="v"
                            stroke="#079455"
                            strokeWidth={1.5}
                            dot={false}
                            isAnimationActive={false}
                          />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                  ) : null}
                </div>
                <div className="flex items-center gap-1 text-[11px] font-medium">
                  {(closed.data?.closureRateTrend ?? 0) >= 0 ? (
                    <span className="flex items-center gap-1 text-[var(--db-success)]">
                      <ArrowUp size={11} aria-hidden />
                      {Math.abs(closed.data?.closureRateTrend ?? 0)} pts vs prior
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-[var(--db-danger)]">
                      <ArrowDown size={11} aria-hidden />
                      {Math.abs(closed.data?.closureRateTrend ?? 0)} pts vs prior
                    </span>
                  )}
                </div>
                <div className="grid grid-cols-2 gap-2 text-[11px]">
                  <div>
                    <p className="text-[var(--db-text-muted)]">Resolved</p>
                    <p className="font-semibold tabular-nums text-[var(--db-text)]">
                      {closed.data?.resolved ?? 0}
                    </p>
                  </div>
                  <div>
                    <p className="text-[var(--db-text-muted)]">False positive</p>
                    <p className="font-semibold tabular-nums text-[var(--db-text)]">
                      {closed.data?.falsePositive ?? 0}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </DashboardPanel>
  )
}
