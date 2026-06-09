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
          className="absolute left-3 top-1/2 -translate-y-1/2 text-white/40 pointer-events-none"
        />
      )}
      <input
        className={cn(
          'w-full h-10 rounded-full border border-glass-border bg-glass-panel backdrop-blur-glass',
          'text-sm text-white placeholder:text-white/38',
          'focus:outline-none focus:ring-2 focus:ring-danger/25 focus:border-glass-border-hover transition',
          showSearchIcon ? 'pl-9 pr-14' : 'px-3',
          shortcut && 'pr-14',
        )}
        {...props}
      />
      {shortcut && (
        <kbd className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] font-medium text-white/50 bg-burgundy-900/80 border border-glass-border rounded px-1.5 py-0.5 leading-none">
          {shortcut}
        </kbd>
      )}
    </div>
  )
}
