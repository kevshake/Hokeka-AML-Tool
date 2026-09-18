import { Search } from 'lucide-react'
import type { InputHTMLAttributes } from 'react'
import { cn } from '../../lib/utils'

export interface GlassInputProps extends InputHTMLAttributes<HTMLInputElement> {
  showSearchIcon?: boolean
  shortcut?: string
}

export default function GlassInput({
  className,
  showSearchIcon = false,
  shortcut,
  ...props
}: GlassInputProps) {
  return (
    <div className={cn('relative', className)}>
      {showSearchIcon && (
        <Search
          size={16}
          className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle pointer-events-none"
        />
      )}
      <input
        className={cn(
          'w-full h-10 rounded border border-hairline-strong bg-burgundy-900 backdrop-blur-glass',
          'text-sm text-ink placeholder:text-ink-subtle',
          'focus:outline-none focus:ring-2 focus:ring-gold/25 focus:border-gold transition',
          'hover:border-gold/50',
          showSearchIcon ? 'pl-9 pr-14' : 'px-3',
          shortcut && 'pr-14',
        )}
        {...props}
      />
      {shortcut && (
        <kbd className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] font-medium text-ink-muted bg-burgundy-800 border border-glass-border rounded px-1.5 py-0.5 leading-none">
          {shortcut}
        </kbd>
      )}
    </div>
  )
}

