import { Info } from 'lucide-react'
import { PolarAngleAxis, RadialBar, RadialBarChart, ResponsiveContainer } from 'recharts'
import DashboardPanel from '../dashboard/DashboardPanel'
import DashboardWidgetHeader from '../dashboard/DashboardWidgetHeader'
import { DashboardEmpty, DashboardError, DashboardLoading } from '../dashboard/DashboardState'
import { useRiskDistribution } from '../../hooks/useDashboard'

function bandColor(score: number) {
  if (score >= 70) return 'var(--db-danger)'
  if (score >= 40) return 'var(--db-warning)'
  return 'var(--db-success)'
}

function bandLabel(score: number) {
  if (score >= 70) return 'Elevated portfolio risk'
  if (score >= 40) return 'Moderate portfolio risk'
  return 'Controlled portfolio risk'
}

function RiskRow({
  color,
  label,
  pct,
  count,
}: {
  color: string
  label: string
  pct: number
  count: number
}) {
  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-xs">
        <div className="flex items-center gap-1.5">
          <span className="inline-block h-2 w-2 rounded-full" style={{ backgroundColor: color }} aria-hidden />
          <span className="text-[var(--db-text-secondary)]">{label}</span>
        </div>
        <span className="font-semibold tabular-nums text-[var(--db-text)]">{pct}%</span>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-[#eceef2]">
        <div className="h-full rounded-full" style={{ width: `${pct}%`, backgroundColor: color }} />
      </div>
      <p className="mt-0.5 text-[10px] text-[var(--db-text-muted)]">{count.toLocaleString()} merchants</p>
    </div>
  )
}

/** Answers: What is the risk mix of our merchant portfolio right now? */
export default function RiskGauge() {
  const { data, isLoading, error } = useRiskDistribution()

  const counts = data ?? {}
  const high = Number(counts['HIGH'] ?? 0)
  const medium = Number(counts['MEDIUM'] ?? 0)
  const low = Number(counts['LOW'] ?? 0)
  const total = high + medium + low

  const score = total > 0 ? Math.round((high * 100 + medium * 60 + low * 20) / total) : 0
  const pct = (n: number) => (total > 0 ? Math.round((n / total) * 100) : 0)
  const fill = bandColor(score)
  const gaugeData = [{ name: 'risk', value: score, fill }]

  return (
    <DashboardPanel aria-labelledby="db-risk-gauge" className="h-full">
      <DashboardWidgetHeader
        id="db-risk-gauge"
        title="Portfolio risk mix"
        description="Weighted score from merchant risk levels"
        trailing={
          <span className="text-[var(--db-text-muted)]" title="Score weights HIGH=100, MEDIUM=60, LOW=20">
            <Info size={14} aria-label="Scoring info" />
          </span>
        }
      />

      {isLoading && !data ? (
        <DashboardLoading rows={5} className="flex-1" />
      ) : error ? (
        <DashboardError message="Risk distribution unavailable." />
      ) : total === 0 ? (
        <DashboardEmpty message="No merchants with risk ratings yet." />
      ) : (
        <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="relative flex min-h-[140px] flex-col items-center justify-center">
            <div className="h-[140px] w-full max-w-[200px]">
              <ResponsiveContainer width="100%" height="100%">
                <RadialBarChart
                  cx="50%"
                  cy="85%"
                  innerRadius="70%"
                  outerRadius="120%"
                  startAngle={180}
                  endAngle={0}
                  data={gaugeData}
                >
                  <PolarAngleAxis type="number" domain={[0, 100]} angleAxisId={0} tick={false} />
                  <RadialBar
                    background={{ fill: '#eceef2' }}
                    dataKey="value"
                    cornerRadius={6}
                    isAnimationActive={false}
                  />
                </RadialBarChart>
              </ResponsiveContainer>
            </div>
            <div className="-mt-10 text-center">
              <p className="text-3xl font-semibold tabular-nums text-[var(--db-text)]">{score}</p>
              <p className="text-[10px] text-[var(--db-text-muted)]">/ 100</p>
              <p className="mt-1 text-xs font-medium" style={{ color: fill }}>
                {bandLabel(score)}
              </p>
            </div>
          </div>

          <div className="flex flex-col justify-center gap-3">
            <p className="db-section-label">Distribution</p>
            <RiskRow color="var(--db-danger)" label="High" pct={pct(high)} count={high} />
            <RiskRow color="var(--db-warning)" label="Medium" pct={pct(medium)} count={medium} />
            <RiskRow color="var(--db-success)" label="Low" pct={pct(low)} count={low} />
          </div>
        </div>
      )}
    </DashboardPanel>
  )
}
