import { motion } from 'framer-motion'
import { ArrowDown, ArrowUp, type LucideIcon } from 'lucide-react'
import GlassCard, { type GlassCardGlowVariant } from '../Common/GlassCard'
import Sparkline, { type SparklineGlow } from './Sparkline'
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
  sparklineGlow?: SparklineGlow
  loading?: boolean
  error?: boolean
  glowVariant?: GlassCardGlowVariant
}

const GLOW_BY_VARIANT: Partial<Record<GlassCardGlowVariant, SparklineGlow>> = {
  burgundy: 'burgundy',
  red: 'red',
  amber: 'amber',
  green: 'green',
  gold: 'gold',
}

export default function KpiCard({
  title,
  subtitle = 'Today',
  value,
  icon: Icon,
  iconBg,
  trend,
  sparklineData,
  sparklineColor = '#C9A96E',
  sparklineGlow,
  loading = false,
  error = false,
  glowVariant,
}: KpiCardProps) {
  const trendPositive = trend?.direction === 'up'
  const trendNegative = trend?.direction === 'down'
  const trendColor = trendPositive
    ? 'text-success'
    : trendNegative
      ? 'text-danger'
      : 'text-glass-muted'

  const glow = sparklineGlow ?? (glowVariant ? GLOW_BY_VARIANT[glowVariant] : undefined) ?? 'gold'

  return (
    <motion.div whileHover={{ y: -1 }} transition={{ duration: 0.2 }}>
      <GlassCard
        padding="sm"
        glowVariant={glowVariant}
        className="flex h-[84px] items-stretch gap-2 !p-3"
      >
        <div className="flex min-w-0 flex-1 flex-col justify-between">
          <div className="flex items-start justify-between gap-1.5">
            <div
              className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-lg"
              style={{ backgroundColor: iconBg }}
            >
              <Icon size={15} color="#FFFFFF" />
            </div>
            <div className="min-w-0 text-right">
              <p className="truncate text-[10px] font-medium leading-tight text-white/80">{title}</p>
              <p className="text-[9px] text-glass-muted">{subtitle}</p>
            </div>
          </div>

          <div>
            {loading ? (
              <div className="h-5 w-16 animate-pulse rounded bg-glass-skeleton" />
            ) : error ? (
              <p className="text-[10px] text-danger">Could not load</p>
            ) : (
              <p className="text-lg font-bold leading-tight text-white">
                {value === null || value === undefined || value === '' ? '—' : value}
              </p>
            )}
          </div>

          <div>
            {trend ? (
              <div className={cn('flex items-center gap-0.5 text-[9px] font-medium', trendColor)}>
                {trendPositive && <ArrowUp size={10} />}
                {trendNegative && <ArrowDown size={10} />}
                <span>
                  {trend.value > 0 ? '+' : ''}
                  {trend.value}%
                </span>
                <span className="text-glass-subtle">{trend.label ?? 'vs yesterday'}</span>
              </div>
            ) : (
              <span className="inline-block h-[14px]" />
            )}
          </div>
        </div>

        {!loading && !error && (
          <div className="flex flex-shrink-0 items-center self-center">
            <Sparkline data={sparklineData} color={sparklineColor} glow={glow} />
          </div>
        )}
      </GlassCard>
    </motion.div>
  )
}
