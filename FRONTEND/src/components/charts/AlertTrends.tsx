import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import DashboardPanel from '../dashboard/DashboardPanel'
import DashboardWidgetHeader from '../dashboard/DashboardWidgetHeader'
import { DashboardEmpty, DashboardError, DashboardLoading } from '../dashboard/DashboardState'
import { useAlertTrends } from '../../hooks/useDashboard'

/** Answers: Is alert volume rising or falling over the last week? */
export default function AlertTrends() {
  const { data, isLoading, error } = useAlertTrends(7)

  const labels = data?.labels ?? []
  const series = data?.data ?? []
  const chartData = labels.map((l, i) => ({ label: l, value: series[i] ?? 0 }))
  const hasData = chartData.some((d) => d.value > 0)

  return (
    <DashboardPanel aria-labelledby="db-alert-trends" className="min-h-[200px]">
      <DashboardWidgetHeader
        id="db-alert-trends"
        title="Alert volume trend"
        description="Daily new alerts · last 7 days"
        actionLabel="Analytics"
        actionTo="/analytics"
      />

      {isLoading && !data ? (
        <DashboardLoading rows={4} className="flex-1" />
      ) : error ? (
        <DashboardError message="Alert trend series unavailable." />
      ) : !hasData ? (
        <DashboardEmpty message="No alerts recorded in this window." />
      ) : (
        <div className="min-h-[120px] w-full flex-1">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 4, right: 6, bottom: 0, left: -18 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="label" tick={{ fontSize: 10 }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fontSize: 10 }} axisLine={false} tickLine={false} allowDecimals={false} />
              <Tooltip
                contentStyle={{
                  borderRadius: 6,
                  border: '1px solid #e4e7ec',
                  background: '#fff',
                  fontSize: 12,
                  color: '#101828',
                  boxShadow: '0 1px 2px rgba(16,24,40,0.06)',
                }}
              />
              <Line
                type="monotone"
                dataKey="value"
                stroke="#d92d20"
                strokeWidth={2}
                dot={{ r: 2.5, fill: '#d92d20', strokeWidth: 0 }}
                activeDot={{ r: 4 }}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </DashboardPanel>
  )
}
