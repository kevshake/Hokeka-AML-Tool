import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useAlertTrends } from '../../hooks/useDashboard'

export default function AlertTrends() {
  const { data, isLoading, error } = useAlertTrends(7)

  const labels = data?.labels ?? []
  const series = data?.data ?? []
  const chartData = labels.map((l, i) => ({ label: l, value: series[i] ?? 0 }))
  const hasData = chartData.length > 0 && chartData.some((d) => d.value > 0)

  return (
    <div className="flex h-full flex-col rounded-2xl border border-hokeka-border bg-hokeka-card p-6">
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h3 className="text-base font-semibold text-slate-900">Alert Trends</h3>
          <p className="text-xs text-slate-400">Last 7 days</p>
        </div>
        <Link
          to="/analytics"
          className="flex items-center gap-1 text-sm font-medium text-hokeka-secondary hover:underline"
        >
          View report <ArrowRight size={14} />
        </Link>
      </div>

      {isLoading ? (
        <div className="h-40 animate-pulse rounded bg-slate-100" />
      ) : error ? (
        <p className="text-sm text-hokeka-critical">Could not load</p>
      ) : !hasData ? (
        <div className="flex h-40 items-center justify-center text-sm text-slate-400">
          No trend data
        </div>
      ) : (
        <div className="h-40 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: -20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" vertical={false} />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 11, fill: '#64748B' }}
                axisLine={false}
                tickLine={false}
              />
              <YAxis
                tick={{ fontSize: 11, fill: '#64748B' }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip
                contentStyle={{
                  borderRadius: 8,
                  border: '1px solid #E2E8F0',
                  fontSize: 12,
                }}
              />
              <Line
                type="monotone"
                dataKey="value"
                stroke="#1F6FEB"
                strokeWidth={2}
                dot={{ r: 3, fill: '#1F6FEB' }}
                activeDot={{ r: 5 }}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}
