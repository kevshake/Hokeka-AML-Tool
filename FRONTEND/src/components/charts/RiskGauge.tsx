import { Info } from 'lucide-react'
import { PolarAngleAxis, RadialBar, RadialBarChart, ResponsiveContainer } from 'recharts'
import { useRiskDistribution } from '../../hooks/useDashboard'

function bandColor(score: number) {
  if (score >= 70) return '#DC2626'
  if (score >= 40) return '#F59E0B'
  return '#00A86B'
}

function bandLabel(score: number) {
  if (score >= 70) return 'High Risk'
  if (score >= 40) return 'Medium Risk'
  return 'Low Risk'
}

export default function RiskGauge() {
  const { data, isLoading, error } = useRiskDistribution()

  const counts = data ?? {}
  const total = Object.values(counts).reduce((a, b) => a + (Number(b) || 0), 0)
  const high = Number(counts['HIGH'] ?? 0)
  const medium = Number(counts['MEDIUM'] ?? 0)
  const low = Number(counts['LOW'] ?? 0)

  // Compose a single overall risk score from distribution: weighted average
  // (HIGH=100, MEDIUM=60, LOW=20). If no merchants exist, the gauge is empty.
  const score =
    total > 0 ? Math.round((high * 100 + medium * 60 + low * 20) / total) : 0

  const pct = (n: number) => (total > 0 ? Math.round((n / total) * 100) : 0)

  const gaugeData = [{ name: 'risk', value: score, fill: bandColor(score) }]

  return (
    <div className="rounded-2xl border border-hokeka-border bg-hokeka-card p-6">
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h3 className="text-base font-semibold text-slate-900">Risk Overview</h3>
          <Info size={14} className="text-slate-400" />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="relative">
          {isLoading ? (
            <div className="h-40 animate-pulse rounded bg-slate-100" />
          ) : error ? (
            <p className="text-sm text-hokeka-critical">Could not load</p>
          ) : (
            <>
              <div className="h-40">
                <ResponsiveContainer width="100%" height="100%">
                  <RadialBarChart
                    cx="50%"
                    cy="90%"
                    innerRadius="100%"
                    outerRadius="160%"
                    startAngle={180}
                    endAngle={0}
                    data={gaugeData}
                  >
                    <PolarAngleAxis
                      type="number"
                      domain={[0, 100]}
                      angleAxisId={0}
                      tick={false}
                    />
                    <RadialBar
                      background={{ fill: '#E2E8F0' }}
                      dataKey="value"
                      cornerRadius={8}
                      isAnimationActive={false}
                    />
                  </RadialBarChart>
                </ResponsiveContainer>
              </div>
              <div className="-mt-12 text-center">
                <p className="text-3xl font-bold text-slate-900">{score}</p>
                <p
                  className="text-sm font-medium"
                  style={{ color: bandColor(score) }}
                >
                  {bandLabel(score)}
                </p>
              </div>
            </>
          )}
        </div>

        <div className="flex flex-col justify-center gap-3">
          <RiskRow color="#DC2626" label="High Risk" pct={pct(high)} count={high} />
          <RiskRow color="#F59E0B" label="Medium Risk" pct={pct(medium)} count={medium} />
          <RiskRow color="#00A86B" label="Low Risk" pct={pct(low)} count={low} />
        </div>
      </div>
    </div>
  )
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
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <span
          className="inline-block h-2.5 w-2.5 rounded-full"
          style={{ backgroundColor: color }}
        />
        <span className="text-sm text-slate-600">{label}</span>
      </div>
      <div className="text-right">
        <span className="text-sm font-semibold text-slate-900">{pct}%</span>
        <span className="ml-2 text-xs text-slate-400">({count})</span>
      </div>
    </div>
  )
}
