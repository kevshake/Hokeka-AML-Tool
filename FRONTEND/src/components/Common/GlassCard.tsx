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
  red: '232, 119, 107',
  orange: '224, 138, 79',
  amber: '226, 178, 93',
  gold: '211, 179, 113',
  green: '117, 183, 171',
  teal: '117, 183, 171',
  purple: '176, 148, 194',
  charcoal: '168, 171, 168',
  burgundy: '211, 179, 113',
}

const GLOW_OPACITY: Record<GlassCardGlowVariant, number> = {
  red: 0.1,
  orange: 0.1,
  amber: 0.1,
  gold: 0.09,
  green: 0.1,
  teal: 0.1,
  purple: 0.09,
  charcoal: 0.07,
  burgundy: 0.09,
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

  const alpha = glowOpacity ?? (glowVariant ? GLOW_OPACITY[glowVariant] : 0.14)

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
        'hokeka-glass-card rounded-2xl',
        hasGlow && 'glass-card-glow relative overflow-hidden',
        !isStatic && 'hover:-translate-y-px',
        paddingMap[padding],
        className,
      )}
      style={{ ...glowVars, ...style }}
    >
      {children}
    </div>
  )
}
