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
        'relative inline-flex items-center justify-center gap-2 overflow-hidden rounded font-semibold transition-all duration-200 ease-editorial',
        'border backdrop-blur-glass',
        size === 'sm' ? 'h-8 px-3 text-xs' : 'h-9 px-4 text-sm',
        variant === 'primary' &&
          'border-gold bg-gold text-charcoal hover:-translate-y-0.5 hover:border-gold-bright hover:bg-gold-bright hover:shadow-cta-hover',
        variant === 'default' &&
          'border-hairline-strong bg-transparent text-ink hover:border-ink hover:bg-ink hover:text-charcoal',
        variant === 'ghost' &&
          'border-transparent bg-transparent text-ink-muted hover:bg-burgundy-800 hover:text-ink',
        'disabled:pointer-events-none disabled:opacity-45',
        className,
      )}
      {...props}
    >
      {children}
    </button>
  )
}
