import { useEffect, useState } from 'react'
import { Bell, ChevronDown } from 'lucide-react'
import GlassInput from '../Common/GlassInput'

interface HokekaHeaderProps {
  userName?: string
  notificationCount?: number
}

function formatTime(d: Date) {
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${hh}:${mm}:${ss}`
}

export default function HokekaHeader({
  userName = 'Admin',
  notificationCount,
}: HokekaHeaderProps) {
  const [now, setNow] = useState(() => formatTime(new Date()))

  useEffect(() => {
    const id = setInterval(() => setNow(formatTime(new Date())), 1000)
    return () => clearInterval(id)
  }, [])

  return (
    <header className="sticky top-0 z-10 flex h-[72px] flex-shrink-0 items-center justify-between gap-4 border-b border-glass-border bg-glass-panel/90 px-5 backdrop-blur-glass">
      <div className="min-w-0 flex-shrink">
        <h1 className="truncate text-xl font-semibold text-white leading-tight">
          Welcome back, {userName} <span aria-hidden>👋</span>
        </h1>
        <p className="mt-0.5 truncate text-xs text-glass-muted">
          Here&apos;s what&apos;s happening in your compliance environment today.
        </p>
      </div>

      <div className="flex flex-shrink-0 items-center gap-3">
        <GlassInput
          placeholder="Search anything..."
          shortcut="⌘K"
          showSearchIcon
          className="w-72 hidden lg:block"
        />

        <div className="hidden h-10 flex-col justify-center rounded-full border border-glass-border bg-glass-panel px-3 sm:flex">
          <div className="flex items-center gap-2">
            <span className="relative inline-flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-60" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-success live-dot-glow" />
            </span>
            <span className="text-sm font-semibold leading-none text-success">LIVE</span>
          </div>
          <span className="mt-0.5 text-[10px] leading-none text-glass-muted">
            Last sync: {now}
          </span>
        </div>

        <button
          type="button"
          className="relative flex h-10 w-10 items-center justify-center rounded-full border border-glass-border bg-glass-panel text-white/85 transition-colors hover:border-glass-border-hover hover:bg-burgundy-850/70 hover:text-white"
          aria-label="Notifications"
        >
          <Bell size={18} />
          {notificationCount !== undefined && notificationCount > 0 && (
            <span className="absolute -top-1 -right-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold leading-none text-white">
              {notificationCount}
            </span>
          )}
        </button>

        <button
          type="button"
          className="flex h-10 items-center gap-2 rounded-full border border-glass-border bg-glass-panel px-2 transition-colors hover:border-glass-border-hover hover:bg-burgundy-850/70"
        >
          <div className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-br from-burgundy-700 to-gold text-xs font-semibold text-white">
            {userName.charAt(0).toUpperCase()}
          </div>
          <ChevronDown size={14} className="hidden text-white/60 sm:block" />
        </button>
      </div>
    </header>
  )
}
