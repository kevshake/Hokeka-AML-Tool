import type { CSSProperties, ReactNode } from 'react'

import { cn } from '../../lib/utils'

export type GlassCardGlowVariant =
  | 'red'
  | 'orange'
  | 'amber'
  | 'gold'
  | 'green'
  | 'teal'
  | 'purple'
  | 'charcoal'
  | 'burgundy'

export type GlassCardGlowPosition = 'top-left' | 'center'

const GLOW_RGB: Record<GlassCardGlowVariant, string> = {
  red: '220, 38, 38',
  orange: '249, 115, 22',
  amber: '245, 158, 11',
  gold: '201, 169, 110',
  green: '34, 197, 94',
  teal: '20, 184, 166',
  purple: '168, 85, 247',
  charcoal: '100, 116, 139',
  burgundy: '123, 35, 50',
}

const GLOW_OPACITY: Record<GlassCardGlowVariant, number> = {
  red: 0.12,
  orange: 0.12,
  amber: 0.12,
  gold: 0.1,
  green: 0.12,
  teal: 0.12,
  purple: 0.12,
  charcoal: 0.08,
  burgundy: 0.12,
}

export interface GlassCardProps {
  children: ReactNode
  className?: string
  style?: CSSProperties
  padding?: 'none' | 'sm' | 'md' | 'lg'
  /** Disable hover glow (e.g. nested cards) */
  static?: boolean
  /** Preset contextual glow color */
  glowVariant?: GlassCardGlowVariant
  /** Custom glow RGB, e.g. "220, 38, 38" — overrides glowVariant color */
  glowColor?: string
  /** Glow origin; gauge widgets use centered halo */
  glowPosition?: GlassCardGlowPosition
  /** Glow strength (0.05–0.15 recommended) */
  glowOpacity?: number
}

const paddingMap = {
  none: '',
  sm: 'p-4',
  md: 'p-5',
  lg: 'p-6',
}

function glowStyleVars(
  glowVariant?: GlassCardGlowVariant,
  glowColor?: string,
  glowPosition: GlassCardGlowPosition = 'top-left',
  glowOpacity?: number,
): CSSProperties | undefined {
  const rgb = glowColor ?? (glowVariant ? GLOW_RGB[glowVariant] : undefined)
  if (!rgb) return undefined

  const alpha = glowOpacity ?? (glowVariant ? GLOW_OPACITY[glowVariant] : 0.12)

  return {
    '--glow-rgb': rgb,
    '--glow-alpha': alpha,
    '--glow-at': glowPosition === 'center' ? 'center' : 'top left',
  } as CSSProperties
}

export default function GlassCard({
  children,
  className,
  style,
  padding = 'lg',
  static: isStatic = false,
  glowVariant,
  glowColor,
  glowPosition = 'top-left',
  glowOpacity,
}: GlassCardProps) {
  const hasGlow = Boolean(glowVariant || glowColor)
  const glowVars = glowStyleVars(glowVariant, glowColor, glowPosition, glowOpacity)

  return (
    <div
      className={cn(
        'rounded-2xl border border-glass-border bg-glass-surface backdrop-blur-glass shadow-glass',
        'ring-1 ring-inset ring-red-950/25',
        hasGlow && 'glass-card-glow relative overflow-hidden',
        !isStatic && 'transition-all duration-200 hover:border-glass-border-hover hover:shadow-glass-glow',
        paddingMap[padding],
        className,
      )}
      style={{ ...glowVars, ...style }}
    >
      {children}
    </div>
  )
}
