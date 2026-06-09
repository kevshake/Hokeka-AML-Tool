import { Line, LineChart, ResponsiveContainer } from 'recharts'
import { cn } from '../../lib/utils'

export type SparklineGlow = 'red' | 'gold' | 'green' | 'amber' | 'burgundy'

const GLOW_CLASS: Record<SparklineGlow, string> = {
  red: 'chart-neon-red',
  gold: 'chart-neon-gold',
  green: 'chart-neon-green',
  amber: 'chart-neon-amber',
  burgundy: 'chart-neon-burgundy',
}

export interface SparklineProps {
  data?: number[]
  color?: string
  glow?: SparklineGlow
  className?: string
  height?: number
  width?: number
}

/** Normalize to at least 2 points so a flat segment renders when data is sparse. */
export function normalizeSparklineData(data?: number[]): number[] | undefined {
  if (!data?.length) return undefined
  if (data.length === 1) return [data[0], data[0]]
  return data
}

export default function Sparkline({
  data,
  color = '#C9A96E',
  glow = 'gold',
  className,
  height = 40,
  width = 56,
}: SparklineProps) {
  const series = normalizeSparklineData(data)
  if (!series) return null

  const chartData = series.map((v, i) => ({ i, v }))

  return (
    <div
      className={cn(GLOW_CLASS[glow], 'flex-shrink-0', className)}
      style={{ width, height }}
      aria-hidden
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={chartData} margin={{ top: 2, right: 0, bottom: 2, left: 0 }}>
          <Line
            type="monotone"
            dataKey="v"
            stroke={color}
            strokeWidth={1.5}
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
