import { memo } from 'react'
import { ArrowDown, ArrowUp, type LucideIcon } from 'lucide-react'
import { Line, LineChart, ResponsiveContainer } from 'recharts'
import { cn } from '../../lib/utils'
import { normalizeSparklineData } from '../kpi/Sparkline'

export interface DashboardKpiTrend {
  value: number
  direction: 'up' | 'down' | 'flat'
  invertSemantic?: boolean
  label?: string
}

export interface DashboardKpiCardProps {
  title: string
  subtitle?: string
  value: string | number | null | undefined
  icon: LucideIcon
  tone?: 'neutral' | 'danger' | 'warning' | 'success' | 'accent' | 'info'
  trend?: DashboardKpiTrend
  sparklineData?: number[]
  loading?: boolean
  error?: boolean
}

const TONE_ICON: Record<NonNullable<DashboardKpiCardProps['tone']>, string> = {
  neutral: 'bg-[var(--surface-3)] text-[var(--muted)]',
  danger: 'bg-[var(--danger-soft)] text-[var(--danger)]',
  warning: 'bg-[var(--warning-soft)] text-[var(--warning)]',
  success: 'bg-[var(--success-soft)] text-[var(--success)]',
  accent: 'bg-[rgb(var(--brand-accent-rgb)/0.12)] text-[var(--gold)]',
  info: 'bg-[var(--info-soft)] text-[var(--info)]',
}

const TONE_STROKE: Record<NonNullable<DashboardKpiCardProps['tone']>, string> = {
  neutral: 'var(--muted)',
  danger: 'var(--danger)',
  warning: 'var(--warning)',
  success: 'var(--success)',
  accent: 'var(--gold)',
  info: 'var(--info)',
}

function DashboardKpiCard({
  title,
  subtitle = 'Today',
  value,
  icon: Icon,
  tone = 'neutral',
  trend,
  sparklineData,
  loading = false,
  error = false,
}: DashboardKpiCardProps) {
  const series = normalizeSparklineData(sparklineData)
  const chartData = series?.map((v, i) => ({ i, v }))

  const upIsGood = !trend?.invertSemantic
  const trendPositive = trend?.direction === 'up'
  const trendNegative = trend?.direction === 'down'
  const trendClass =
    trend?.direction === 'flat'
      ? 'text-[var(--db-text-muted)]'
      : (trendPositive && upIsGood) || (trendNegative && !upIsGood)
        ? 'text-[var(--db-success)]'
        : 'text-[var(--db-danger)]'

  return (
    <article className="db-kpi flex min-h-[108px] flex-col gap-2.5 p-4" aria-busy={loading}>
      <div className="flex items-start gap-2.5">
        <span
          className={cn(
            'mt-0.5 flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-md',
            TONE_ICON[tone],
          )}
          aria-hidden
        >
          <Icon size={13} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-[11px] font-semibold leading-snug text-[var(--db-text)]">{title}</p>
          <p className="text-[10px] leading-snug text-[var(--db-text-muted)]">{subtitle}</p>
        </div>
      </div>

      <div className="flex items-end justify-between gap-3">
        <div className="min-w-0 flex-1">
          {loading ? (
            <div className="db-skel h-7 w-20" />
          ) : error ? (
            <p className="text-xs text-[var(--db-danger)]" role="alert">
              Unavailable
            </p>
          ) : (
            <p className="text-[1.35rem] font-semibold leading-none tracking-tight text-[var(--db-text)]">
              {value === null || value === undefined || value === '' ? '—' : value}
            </p>
          )}

          <div className="mt-1.5 min-h-[14px]">
            {!loading && !error && trend ? (
              <div className={cn('flex flex-wrap items-center gap-0.5 text-[10px] font-medium', trendClass)}>
                {trendPositive && <ArrowUp size={10} aria-hidden />}
                {trendNegative && <ArrowDown size={10} aria-hidden />}
                <span>
                  {trend.value > 0 ? '+' : ''}
                  {trend.value}%
                </span>
                <span className="font-normal text-[var(--db-text-muted)]">
                  {trend.label ?? 'vs yesterday'}
                </span>
              </div>
            ) : null}
          </div>
        </div>

        {!loading && !error && chartData ? (
          <div className="h-10 w-16 flex-shrink-0" aria-hidden>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 2, right: 0, bottom: 2, left: 0 }}>
                <Line
                  type="monotone"
                  dataKey="v"
                  stroke={TONE_STROKE[tone]}
                  strokeWidth={1.5}
                  dot={false}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        ) : null}
      </div>
    </article>
  )
}

export default memo(DashboardKpiCard)
