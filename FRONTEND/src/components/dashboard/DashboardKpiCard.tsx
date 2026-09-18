import { memo } from 'react'
import { ArrowDown, ArrowUp, type LucideIcon } from 'lucide-react'
import { Line, LineChart, ResponsiveContainer } from 'recharts'
import { cn } from '../../lib/utils'
import { normalizeSparklineData } from '../kpi/Sparkline'

export interface DashboardKpiTrend {
  value: number
  direction: 'up' | 'down' | 'flat'
  /** When true, "up" is bad (e.g. more alerts). Flips success/danger colors. */
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
  neutral: 'bg-[#f2f4f7] text-[var(--db-text-secondary)]',
  danger: 'bg-[var(--db-danger-soft)] text-[var(--db-danger)]',
  warning: 'bg-[var(--db-warning-soft)] text-[var(--db-warning)]',
  success: 'bg-[var(--db-success-soft)] text-[var(--db-success)]',
  accent: 'bg-[var(--db-accent-soft)] text-[var(--db-accent)]',
  info: 'bg-[var(--db-info-soft)] text-[var(--db-info)]',
}

const TONE_STROKE: Record<NonNullable<DashboardKpiCardProps['tone']>, string> = {
  neutral: '#667085',
  danger: '#d92d20',
  warning: '#b54708',
  success: '#079455',
  accent: '#7a1f3d',
  info: '#175cd3',
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
    <article className="db-kpi flex min-h-[96px] flex-col justify-between gap-2 p-3.5" aria-busy={loading}>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-[11px] font-medium text-[var(--db-text-secondary)]">{title}</p>
          <p className="text-[10px] text-[var(--db-text-muted)]">{subtitle}</p>
        </div>
        <span
          className={cn(
            'flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md',
            TONE_ICON[tone],
          )}
          aria-hidden
        >
          <Icon size={14} />
        </span>
      </div>

      <div className="flex items-end justify-between gap-2">
        <div className="min-w-0">
          {loading ? (
            <div className="db-skel h-7 w-16" />
          ) : error ? (
            <p className="text-xs text-[var(--db-danger)]" role="alert">
              Unavailable
            </p>
          ) : (
            <p className="truncate text-[22px] font-semibold leading-none tracking-tight text-[var(--db-text)]">
              {value === null || value === undefined || value === '' ? '—' : value}
            </p>
          )}

          <div className="mt-1.5 min-h-[14px]">
            {!loading && !error && trend ? (
              <div className={cn('flex items-center gap-0.5 text-[10px] font-medium', trendClass)}>
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
          <div className="h-9 w-14 flex-shrink-0" aria-hidden>
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
