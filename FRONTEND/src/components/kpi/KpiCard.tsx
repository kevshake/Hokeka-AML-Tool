import { motion } from 'framer-motion'
import { ArrowDown, ArrowUp, type LucideIcon } from 'lucide-react'
import { Line, LineChart, ResponsiveContainer } from 'recharts'
import { cn } from '../../lib/utils'

export interface KpiTrend {
  value: number
  direction: 'up' | 'down' | 'flat'
  label?: string
}

export interface KpiCardProps {
  title: string
  subtitle?: string
  value: string | number | null | undefined
  icon: LucideIcon
  iconBg: string
  trend?: KpiTrend
  sparklineData?: number[]
  sparklineColor?: string
  loading?: boolean
  error?: boolean
}

export default function KpiCard({
  title,
  subtitle = 'Today',
  value,
  icon: Icon,
  iconBg,
  trend,
  sparklineData,
  sparklineColor = '#1F6FEB',
  loading = false,
  error = false,
}: KpiCardProps) {
  const trendPositive = trend?.direction === 'up'
  const trendNegative = trend?.direction === 'down'
  const trendColor = trendPositive
    ? 'text-hokeka-success'
    : trendNegative
      ? 'text-hokeka-critical'
      : 'text-slate-500'

  const sparkData = (sparklineData ?? []).map((v, i) => ({ i, v }))

  return (
    <motion.div
      whileHover={{ y: -4 }}
      transition={{ duration: 0.2 }}
      className="rounded-2xl border border-hokeka-border bg-hokeka-card p-4 shadow-sm"
    >
      <div className="flex items-start justify-between">
        <div
          className="flex h-10 w-10 items-center justify-center rounded-xl"
          style={{ backgroundColor: iconBg }}
        >
          <Icon size={20} color="#FFFFFF" />
        </div>
        <div className="text-right">
          <p className="text-sm font-medium text-slate-600">{title}</p>
          <p className="text-xs text-slate-400">{subtitle}</p>
        </div>
      </div>

      <div className="my-2">
        {loading ? (
          <div className="h-8 w-24 animate-pulse rounded bg-slate-100" />
        ) : error ? (
          <p className="text-sm text-hokeka-critical">Could not load</p>
        ) : (
          <p className="text-3xl font-bold text-slate-900">
            {value === null || value === undefined || value === '' ? '—' : value}
          </p>
        )}
      </div>

      <div className="flex items-center justify-between">
        {trend ? (
          <div className={cn('flex items-center gap-1 text-xs font-medium', trendColor)}>
            {trendPositive && <ArrowUp size={12} />}
            {trendNegative && <ArrowDown size={12} />}
            <span>
              {trend.value > 0 ? '+' : ''}
              {trend.value}%
            </span>
            <span className="text-slate-400">{trend.label ?? 'vs yesterday'}</span>
          </div>
        ) : (
          <span />
        )}
        {sparkData.length > 1 && (
          <div className="h-6 w-16">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={sparkData}>
                <Line
                  type="monotone"
                  dataKey="v"
                  stroke={sparklineColor}
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
    </motion.div>
  )
}
