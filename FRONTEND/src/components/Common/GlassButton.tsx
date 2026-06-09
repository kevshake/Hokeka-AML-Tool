import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { cn } from '../../lib/utils'

export interface GlassButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode
  variant?: 'default' | 'primary' | 'ghost'
  size?: 'sm' | 'md'
}

export default function GlassButton({
  children,
  className,
  variant = 'default',
  size = 'md',
  ...props
}: GlassButtonProps) {
  return (
    <button
      type="button"
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-xl font-medium transition-all',
        'border backdrop-blur-glass',
        size === 'sm' ? 'h-8 px-3 text-xs' : 'h-9 px-4 text-sm',
        variant === 'primary' &&
          'border-burgundy-700/60 bg-gradient-to-r from-burgundy-700 to-burgundy-900 text-white shadow-neon-red hover:from-burgundy-800 hover:to-burgundy-950 hover:shadow-glass-glow',
        variant === 'default' &&
          'border-glass-border bg-glass-panel text-white/90 hover:border-glass-border-hover hover:bg-burgundy-850/70 hover:text-white',
        variant === 'ghost' &&
          'border-transparent bg-transparent text-white/70 hover:bg-burgundy-850/50 hover:text-white',
        className,
      )}
      {...props}
    >
      {children}
    </button>
  )
}
